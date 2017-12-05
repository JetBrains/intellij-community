class DecrementDifferentOuterFieldsDuplicate {
    int x;
    int y;

    class C {
        private void foo() {

            newMethod();

            if (y > 0) {
                bar(y);
                DecrementDifferentOuterFieldsDuplicate.this.y--;
            }
        }

        private void newMethod() {
            if (x > 0) {
                bar(x);
                DecrementDifferentOuterFieldsDuplicate.this.x--;
            }
        }
    }

    private void bar(int i) { }
}