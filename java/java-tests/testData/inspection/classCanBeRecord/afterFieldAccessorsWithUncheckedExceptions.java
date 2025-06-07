// "Convert to record class" "true-preview"
record R(int first, int second, int third) {
    /**
     * @throws NullPointerException
     */
    R {
    }

    /**
     * some doc
     *
     * @throws NullPointerException
     * @throws
     * @throws ArithmeticException
     */
    @Override
    public int first() {
        return first;
    }

    /**
     * @throws NullPointerException
     * @throws ArithmeticException
     */
    @Override
    public int third() {
        return third > 0 ? third : -third;
    }
}
