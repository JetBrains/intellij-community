class Test {
    String test(int x) {
        if (x > 0) {
            return "";
        }
        <selection>if (x < 0) {
            throw new IllegalArgumentException();
        } else {
            throw new IllegalStateException();
        }</selection>
    }
}
