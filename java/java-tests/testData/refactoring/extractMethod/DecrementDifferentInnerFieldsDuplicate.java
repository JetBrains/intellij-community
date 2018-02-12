class DecrementDifferentInnerFieldsDuplicate {
    class C {
        int x;
        int y;
    }

    private void foo(C a, C b) {
        <selection>
        if (a.x > 0) {
            bar(a.x);
            a.x--;
        }</selection>

        if (b.y > 0) {
            bar(b.y);
            b.y--;
        }
    }

    private void bar(int i) { }
}