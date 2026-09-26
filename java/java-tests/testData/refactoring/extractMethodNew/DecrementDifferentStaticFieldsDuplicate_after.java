class DecrementDifferentStaticFieldsDuplicate {
    static class C {
        static int x;
        static int y;
    }

    private void foo() {

        newMethod();

        if (C.y > 0) {
            bar(C.y);
            C.y--;
        }
    }

    private void newMethod() {
        if (C.x > 0) {
            bar(C.x);
            C.x--;
        }
    }

    private void bar(int i) { }
}