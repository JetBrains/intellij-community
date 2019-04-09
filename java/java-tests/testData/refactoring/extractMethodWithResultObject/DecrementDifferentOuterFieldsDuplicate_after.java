class DecrementDifferentOuterFieldsDuplicate {
    int x;
    int y;

    class C {
        private void foo() {

            NewMethodResult x1 = newMethod();

            if (y > 0) {
                bar(y);
                DecrementDifferentOuterFieldsDuplicate.this.y--;
            }
        }

        NewMethodResult newMethod() {
            if (x > 0) {
                bar(x);
                DecrementDifferentOuterFieldsDuplicate.this.x--;
            }
            return new NewMethodResult();
        }

        class NewMethodResult {
            public NewMethodResult() {
            }
        }
    }

    private void bar(int i) { }
}