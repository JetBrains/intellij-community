class test {
    private static final boolean DEBUG = false;

    private test field;

    private test(test field) {
        this.field = field;
    }

    private void method() {
        if (DEBUG) {
            field.method();
        }
    }
}
