class DecrementDifferentStaticFieldsDuplicate {
    static class C {
        static int x;
        static int y;
    }

    private void foo() {
        <selection>
        if (C.x > 0) {
            bar(C.x);
            C.x--;
        }</selection>

        if (C.y > 0) {
            bar(C.y);
            C.y--;
        }
    }

    private void bar(int i) { }
}