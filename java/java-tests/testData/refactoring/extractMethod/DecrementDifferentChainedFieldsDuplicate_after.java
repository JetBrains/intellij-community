class DecrementDifferentChainedFieldsDuplicate {
    static class C {
        int x;
        int y;
    }
    public static final C c;

    private void foo() {

        newMethod();

        if (c.y > 0) {
            bar(c.y);
            c.y--;
        }
    }

    private void newMethod() {
        if (c.x > 0) {
            bar(c.x);
            c.x--;
        }
    }

    private void bar(int i) { }
}