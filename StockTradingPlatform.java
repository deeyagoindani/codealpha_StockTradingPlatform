import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * StockTradingPlatform.java
 * Console-based stock trading simulator with:
 * - Simulated market data (random walk)
 * - Buy/Sell operations
 * - Portfolio + transaction history
 * - Performance tracking over time
 * - File I/O persistence (cash, holdings, history)
 *
 * How to run:
 *   javac StockTradingPlatform.java
 *   java StockTradingPlatform
 */
public class StockTradingPlatform {

    // ---------- Data Models ----------
    static class Stock {
        private final String symbol;
        private final String name;
        private double price;

        public Stock(String symbol, String name, double price) {
            this.symbol = symbol.toUpperCase();
            this.name = name;
            this.price = price;
        }
        public String getSymbol() { return symbol; }
        public String getName() { return name; }
        public double getPrice() { return price; }
        public void setPrice(double price) { this.price = Math.max(0.01, price); }

        @Override
        public String toString() {
            return String.format("%-6s %-18s $%.2f", symbol, name, price);
        }
    }

    static class Transaction {
        enum Type { BUY, SELL }
        final LocalDate date;
        final String symbol;
        final Type type;
        final int quantity;
        final double price;
        final double amount; // price * qty

        Transaction(LocalDate date, String symbol, Type type, int quantity, double price) {
            this.date = date;
            this.symbol = symbol;
            this.type = type;
            this.quantity = quantity;
            this.price = price;
            this.amount = price * quantity;
        }

        @Override
        public String toString() {
            return String.format("%s %s %d @ $%.2f = $%.2f",
                    date, type, quantity, price, amount);
        }
    }

    static class PortfolioItem {
        final String symbol;
        int quantity;

        PortfolioItem(String symbol, int quantity) {
            this.symbol = symbol;
            this.quantity = quantity;
        }
    }

    static class Snapshot {
        final LocalDate date;
        final double totalValue;
        Snapshot(LocalDate d, double v) { date = d; totalValue = v; }
    }

    static class UserAccount {
        private double cash;
        private final Map<String, PortfolioItem> holdings = new HashMap<>();
        private final List<Transaction> transactions = new ArrayList<>();
        private final List<Snapshot> performance = new ArrayList<>();

        public UserAccount(double initialCash) { this.cash = initialCash; }
        public double getCash() { return cash; }
        public Map<String, PortfolioItem> getHoldings() { return holdings; }
        public List<Transaction> getTransactions() { return transactions; }
        public List<Snapshot> getPerformance() { return performance; }

        public int getPosition(String symbol) {
            return holdings.getOrDefault(symbol, new PortfolioItem(symbol,0)).quantity;
        }

        public void adjustPosition(String symbol, int deltaQty) {
            PortfolioItem item = holdings.get(symbol);
            if (item == null) {
                item = new PortfolioItem(symbol, 0);
                holdings.put(symbol, item);
            }
            item.quantity += deltaQty;
            if (item.quantity <= 0) holdings.remove(symbol);
        }

        public void deposit(double amount) { cash += amount; }
        public boolean withdraw(double amount) {
            if (amount <= cash + 1e-9) { cash -= amount; return true; }
            return false;
        }
    }

    static class Market {
        private final Map<String, Stock> stocks = new LinkedHashMap<>();
        private final Random rng = new Random();

        public void addStock(Stock s) { stocks.put(s.getSymbol(), s); }
        public Stock get(String symbol) { return stocks.get(symbol.toUpperCase()); }
        public Collection<Stock> list() { return stocks.values(); }

        // Simulate a "day" of price movement (random walk within ±3%)
        public void tick() {
            for (Stock s : stocks.values()) {
                double pct = (rng.nextDouble() * 6.0) - 3.0; // -3%..+3%
                double newPrice = s.getPrice() * (1.0 + pct/100.0);
                s.setPrice(newPrice);
            }
        }
    }

    // ---------- Persistence ----------
    static final String DATA_DIR = "portfolio_data";
    static final String CASH_FILE = DATA_DIR + File.separator + "cash.txt";
    static final String HOLDINGS_FILE = DATA_DIR + File.separator + "holdings.csv";
    static final String HISTORY_FILE = DATA_DIR + File.separator + "history.csv";
    static final DateTimeFormatter DF = DateTimeFormatter.ISO_LOCAL_DATE;

    static void ensureDataDir() {
        File dir = new File(DATA_DIR);
        if (!dir.exists()) dir.mkdirs();
    }

    static void save(UserAccount acct) {
        ensureDataDir();
        try (PrintWriter pw = new PrintWriter(new FileWriter(CASH_FILE))) {
            pw.println(acct.getCash());
        } catch (IOException e) { System.out.println("Failed to save cash: " + e.getMessage()); }

        try (PrintWriter pw = new PrintWriter(new FileWriter(HOLDINGS_FILE))) {
            pw.println("symbol,quantity");
            for (PortfolioItem it : acct.getHoldings().values()) {
                pw.println(it.symbol + "," + it.quantity);
            }
        } catch (IOException e) { System.out.println("Failed to save holdings: " + e.getMessage()); }

        try (PrintWriter pw = new PrintWriter(new FileWriter(HISTORY_FILE))) {
            pw.println("date,total_value");
            for (Snapshot s : acct.getPerformance()) {
                pw.println(DF.format(s.date) + "," + s.totalValue);
            }
        } catch (IOException e) { System.out.println("Failed to save history: " + e.getMessage()); }
    }

    static void load(UserAccount acct) {
        ensureDataDir();
        // Cash
        File cf = new File(CASH_FILE);
        if (cf.exists()) {
            try (Scanner sc = new Scanner(cf)) {
                if (sc.hasNextDouble()) {
                    double cash = sc.nextDouble();
                    acct.deposit(cash - acct.getCash()); // set to file value
                }
            } catch (Exception ignored) {}
        }
        // Holdings
        File hf = new File(HOLDINGS_FILE);
        if (hf.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(hf))) {
                String line = br.readLine(); // header
                while ((line = br.readLine()) != null) {
                    String[] t = line.split(",");
                    if (t.length == 2) {
                        String sym = t[0].trim().toUpperCase();
                        int qty = Integer.parseInt(t[1].trim());
                        acct.adjustPosition(sym, qty);
                    }
                }
            } catch (Exception ignored) {}
        }
        // History
        File pf = new File(HISTORY_FILE);
        if (pf.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(pf))) {
                String line = br.readLine(); // header
                while ((line = br.readLine()) != null) {
                    String[] t = line.split(",");
                    if (t.length == 2) {
                        LocalDate d = LocalDate.parse(t[0].trim(), DF);
                        double val = Double.parseDouble(t[1].trim());
                        acct.getPerformance().add(new Snapshot(d, val));
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    // ---------- Utility ----------
    static double portfolioMarketValue(UserAccount acct, Market market) {
        double total = 0.0;
        for (PortfolioItem it : acct.getHoldings().values()) {
            Stock s = market.get(it.symbol);
            if (s != null) total += s.getPrice() * it.quantity;
        }
        return total;
    }

    static void recordSnapshot(UserAccount acct, Market market, LocalDate date) {
        double value = acct.getCash() + portfolioMarketValue(acct, market);
        acct.getPerformance().add(new Snapshot(date, value));
    }

    static void printMarket(Market market) {
        System.out.println("\n--- MARKET DATA ---");
        System.out.printf("%-6s %-18s %10s%n", "SYM", "NAME", "PRICE");
        for (Stock s : market.list()) {
            System.out.printf("%-6s %-18s $%10.2f%n", s.getSymbol(), s.getName(), s.getPrice());
        }
    }

    static void printPortfolio(UserAccount acct, Market market) {
        System.out.println("\n--- PORTFOLIO ---");
        System.out.printf("Cash: $%.2f%n", acct.getCash());
        if (acct.getHoldings().isEmpty()) {
            System.out.println("(no positions)");
        } else {
            System.out.printf("%-6s %10s %12s %14s%n", "SYM", "QTY", "PRICE", "MKT VALUE");
            for (PortfolioItem it : acct.getHoldings().values()) {
                Stock s = market.get(it.symbol);
                double price = (s==null)?0.0:s.getPrice();
                System.out.printf("%-6s %10d %12.2f %14.2f%n",
                        it.symbol, it.quantity, price, price*it.quantity);
            }
        }
        double total = acct.getCash() + portfolioMarketValue(acct, market);
        System.out.printf("Total Account Value: $%.2f%n", total);
    }

    static void printPerformance(UserAccount acct) {
        System.out.println("\n--- PERFORMANCE HISTORY ---");
        if (acct.getPerformance().isEmpty()) {
            System.out.println("(no snapshots yet — advance the market or record a snapshot)");
            return;
        }
        Snapshot first = acct.getPerformance().get(0);
        for (Snapshot s : acct.getPerformance()) {
            double change = ((s.totalValue - first.totalValue) / first.totalValue) * 100.0;
            System.out.printf("%s  $%.2f  (%+.2f%% since start)%n",
                    DF.format(s.date), s.totalValue, change);
        }
    }

    // ---------- Trading ----------
    static void buy(UserAccount acct, Market market, Scanner in) {
        System.out.print("Enter symbol to BUY: ");
        String sym = in.next().toUpperCase();
        Stock s = market.get(sym);
        if (s == null) { System.out.println("Unknown symbol."); return; }

        System.out.printf("Price $%.2f. Quantity: ", s.getPrice());
        int qty = in.nextInt();
        if (qty <= 0) { System.out.println("Quantity must be positive."); return; }

        double cost = s.getPrice() * qty;
        if (!acct.withdraw(cost)) {
            System.out.println("Insufficient cash.");
            return;
        }
        acct.adjustPosition(sym, qty);
        Transaction t = new Transaction(LocalDate.now(), sym, Transaction.Type.BUY, qty, s.getPrice());
        acct.getTransactions().add(t);
        System.out.println("Bought: " + t);
    }

    static void sell(UserAccount acct, Market market, Scanner in) {
        System.out.print("Enter symbol to SELL: ");
        String sym = in.next().toUpperCase();
        Stock s = market.get(sym);
        if (s == null) { System.out.println("Unknown symbol."); return; }

        int pos = acct.getPosition(sym);
        if (pos <= 0) { System.out.println("You do not own " + sym); return; }

        System.out.printf("You own %d. Quantity to sell: ", pos);
        int qty = in.nextInt();
        if (qty <= 0 || qty > pos) { System.out.println("Invalid quantity."); return; }

        double proceeds = s.getPrice() * qty;
        acct.deposit(proceeds);
        acct.adjustPosition(sym, -qty);
        Transaction t = new Transaction(LocalDate.now(), sym, Transaction.Type.SELL, qty, s.getPrice());
        acct.getTransactions().add(t);
        System.out.println("Sold: " + t);
    }

    // ---------- App ----------
    public static void main(String[] args) {
        // Market setup
        Market market = new Market();
        market.addStock(new Stock("AAPL", "Apple Inc.", 190.00));
        market.addStock(new Stock("MSFT", "Microsoft", 420.00));
        market.addStock(new Stock("GOOGL", "Alphabet", 165.00));
        market.addStock(new Stock("AMZN", "Amazon", 180.00));
        market.addStock(new Stock("TSLA", "Tesla", 250.00));

        // User account (default $10,000, then load persisted state if any)
        UserAccount acct = new UserAccount(10_000.00);
        load(acct);
        if (acct.getPerformance().isEmpty()) {
            recordSnapshot(acct, market, LocalDate.now());
        }

        Scanner in = new Scanner(System.in);
        boolean running = true;

        while (running) {
            System.out.println("\n===== STOCK TRADING PLATFORM =====");
            System.out.println("1) View Market");
            System.out.println("2) Advance Market (new day)");
            System.out.println("3) Buy");
            System.out.println("4) Sell");
            System.out.println("5) View Portfolio");
            System.out.println("6) View Performance");
            System.out.println("7) View Transactions");
            System.out.println("8) Record Snapshot (today)");
            System.out.println("9) Save & Exit");
            System.out.print("Choose: ");

            int choice;
            try { choice = Integer.parseInt(in.next()); }
            catch (Exception e) { System.out.println("Invalid input."); continue; }

            switch (choice) {
                case 1 -> printMarket(market);
                case 2 -> {
                    market.tick();
                    System.out.println("Market advanced one day.");
                    recordSnapshot(acct, market, LocalDate.now());
                }
                case 3 -> buy(acct, market, in);
                case 4 -> sell(acct, market, in);
                case 5 -> printPortfolio(acct, market);
                case 6 -> printPerformance(acct);
                case 7 -> {
                    System.out.println("\n--- TRANSACTIONS ---");
                    if (acct.getTransactions().isEmpty()) System.out.println("(none)");
                    else acct.getTransactions().forEach(t -> System.out.println(t.date + " " + t.type + " " + t.symbol + " x" + t.quantity + " @ $" + String.format("%.2f", t.price)));
                }
                case 8 -> {
                    recordSnapshot(acct, market, LocalDate.now());
                    System.out.println("Snapshot recorded.");
                }
                case 9 -> {
                    save(acct);
                    System.out.println("Saved. Bye!");
                    running = false;
                }
                default -> System.out.println("Unknown option.");
            }
        }
    }
}
