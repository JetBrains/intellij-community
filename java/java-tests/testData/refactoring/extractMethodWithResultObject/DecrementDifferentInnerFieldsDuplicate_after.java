class DecrementDifferentInnerFieldsDuplicate {
    class C {
        int x;
        int y;
    }

    private void foo(C a, C b) {

        NewMethodResult x = newMethod(a);

        if (b.y > 0) {
            bar(b.y);
            b.y--;
        }
    }

    NewMethodResult newMethod(C a) {
        if (a.x > 0) {
            bar(a.x);
            a.x--;
        }
        return new NewMethodResult();
    }

    static class NewMethodResult {
        public NewMethodResult() {
        }
    }

    private void bar(int i) { }
}