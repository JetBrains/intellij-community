public enum Operation {
    PLUS {
        int eval(int x, int y) {
            return x + y;
        }
    };

    abstract int eval(int x, int y);

    Operation() {
    }
}