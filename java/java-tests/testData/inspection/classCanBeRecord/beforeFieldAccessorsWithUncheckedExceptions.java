// "Convert to record class" "true-preview"
class <caret>R {
    final int first;
    final int second;
    final int third;

    private R(int first, int second, int third) throws NullPointerException {
        this.first = first;
        this.second = second;
        this.third = third;
    }

    /**
     * some doc
     * @throws NullPointerException
     * @throws
     */
    int first() throws NullPointerException, ArithmeticException {
        return first;
    }

    // it's a default accessor without java doc, so throws list doesn't make sense
    int second() throws NullPointerException, ArithmeticException {
        return second;
    }

    int third() throws NullPointerException, ArithmeticException {
        return third > 0 ? third : -third;
    }
}
