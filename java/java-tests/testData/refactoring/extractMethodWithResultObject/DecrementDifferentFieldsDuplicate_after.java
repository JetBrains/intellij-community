class DecrementDifferentFieldsDuplicate {
    int x;
    int y;

    private void foo() {

        NewMethodResult x1 = newMethod();

        if (y > 0) {
            bar(y);
            y--;
        }
    }

    NewMethodResult newMethod() {
        if (x > 0) {
            bar(x);
            x--;
        }
        return new NewMethodResult();
    }

    static class NewMethodResult {
        public NewMethodResult() {
        }
    }

    private void bar(int i) { }
}