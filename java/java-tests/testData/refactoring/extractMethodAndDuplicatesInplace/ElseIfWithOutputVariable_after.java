class Test {
    int test(boolean condition, int value, int result) {
        if (condition) {
            result = 0;
        } else result = getResult(value, result);
        return result;
    }

    private static int getResult(int value, int result) {
        if (value > 0) {
            result = value;
        }
        return result;
    }
}