class DecrementDifferentInnerFieldsDuplicate {
    class C {
        int x;
        int y;
    }

    private void foo(C a, C b) {

        newMethod(a);

        if (b.y > 0) {
            bar(b.y);
            b.y--;
        }
    }

    private void newMethod(C a) {
        if (a.x > 0) {
            bar(a.x);
            a.x--;
        }
    }

    private void bar(int i) { }
}