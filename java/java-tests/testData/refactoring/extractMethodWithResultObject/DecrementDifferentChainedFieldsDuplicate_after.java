class DecrementDifferentChainedFieldsDuplicate {
    static class C {
        int x;
        int y;
    }
    public static final C c = new C();

    private void foo() {

        NewMethodResult x = newMethod();

        if (c.y > 0) {
            bar(c.y);
            c.y--;
        }
    }

    NewMethodResult newMethod() {
        if (c.x > 0) {
            bar(c.x);
            c.x--;
        }
        return new NewMethodResult();
    }

    static class NewMethodResult {
        public NewMethodResult() {
        }
    }

    private void bar(int i) { }
}