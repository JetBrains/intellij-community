class Getter {
    BidAsk get(String s) {
        return null;
    }
}

class BidAsk {
    public Object getOpenQuote;
}

public class A {
    static Object f(Getter prices) {
        return System.currentTimeMillis() == 2.0 ?   ((BidAsk) prices.get(null)).getOpenQuote : null;
    }
}
