class Getter {
    BidAsk get(String s) {
        return null;
    }
}

class BidAsk {
    public Object getOpenQuote;
}

class A {
    static Object f(Getter prices) {
        return System.currentTimeMillis() == 2.0 ?   ((<warning descr="Casting 'prices.get(...)' to 'BidAsk' is redundant">BidAsk</warning>) prices.get(null)).getOpenQuote : null;
    }
}
