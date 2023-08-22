// "Remove switch branch 'A'" "true-preview"
class X {
    enum X {A,B,C}

    void test(X x) {
        if (x == X.A) return;
        int res = switch(x) {
            case B -> 2;
            case C -> 3;
            default -> throw new IllegalStateException("Unexpected value: " + x);
        };
    }
}