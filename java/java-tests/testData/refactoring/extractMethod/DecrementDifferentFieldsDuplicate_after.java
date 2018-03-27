class DecrementDifferentFieldsDuplicate {
    int x;
    int y;

    private void foo() {

        newMethod();

        if (y > 0) {
            bar(y);
            y--;
        }
    }

    private void newMethod() {
        if (x > 0) {
            bar(x);
            x--;
        }
    }

    private void bar(int i) { }
}