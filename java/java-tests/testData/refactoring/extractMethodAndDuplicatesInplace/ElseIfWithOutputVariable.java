class Test {
    int test(boolean condition, int value, int result) {
        if (condition) {
            result = 0;
        } else <selection>if (value > 0) {
            result = value;
        }</selection>
        return result;
    }
}