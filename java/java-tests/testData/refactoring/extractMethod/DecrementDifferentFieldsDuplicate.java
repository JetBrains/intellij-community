class DecrementDifferentFieldsDuplicate {
    int x;
    int y;

    private void foo() {
        <selection>
        if (x > 0) {
            bar(x);
            x--;
        }</selection>

        if (y > 0) {
            bar(y);
            y--;
        }
    }

    private void bar(int i) { }
}