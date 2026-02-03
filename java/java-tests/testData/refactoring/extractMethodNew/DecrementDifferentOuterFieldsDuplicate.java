class DecrementDifferentOuterFieldsDuplicate {
    int x;
    int y;

    class C {
        private void foo() {
            <selection>
            if (x > 0) {
                bar(x);
                DecrementDifferentOuterFieldsDuplicate.this.x--;
            }</selection>

            if (y > 0) {
                bar(y);
                DecrementDifferentOuterFieldsDuplicate.this.y--;
            }
        }
    }

    private void bar(int i) { }
}