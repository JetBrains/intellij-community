class A {
    String test() {
        return new <caret>Holder("x").get();
    }
}

class Holder {
    public static final String PREFIX = "p";
    private final String s;

    Holder(String s) {
        this.s = PREFIX + s;
    }

    String get() {
        return s;
    }
}