class DecrementDifferentStaticFieldsDuplicate {
    static class C {
        static int x;
        static int y;
    }

    private void foo() {

        NewMethodResult x = newMethod();

        if (C.y > 0) {
            bar(C.y);
            C.y--;
        }
    }

    NewMethodResult newMethod() {
        if (C.x > 0) {
            bar(C.x);
            C.x--;
        }
        return new NewMethodResult();
    }

    static class NewMethodResult {
        public NewMethodResult() {
        }
    }

    private void bar(int i) { }
}