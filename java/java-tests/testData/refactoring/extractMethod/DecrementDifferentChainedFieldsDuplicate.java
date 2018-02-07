class DecrementDifferentChainedFieldsDuplicate {
    static class C {
        int x;
        int y;
    }
    public static final C c;

    private void foo() {
        <selection>
        if (c.x > 0) {
            bar(c.x);
            c.x--;
        }</selection>

        if (c.y > 0) {
            bar(c.y);
            c.y--;
        }
    }

    private void bar(int i) { }
}