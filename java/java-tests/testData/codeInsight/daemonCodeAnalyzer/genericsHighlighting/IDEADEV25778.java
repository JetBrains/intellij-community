class Price {
    public <PT extends Price> PT clone() {
        return null;
    }
}

class BondPrice extends Price {
    public <PT extends BondPrice> PT clone() {
        return null;
    }
}

class User {
    public static void main(String[] args) {
        new BondPrice().clone<error descr="Ambiguous method call: both 'BondPrice.clone()' and 'Price.clone()' match">()</error>;
    }
}