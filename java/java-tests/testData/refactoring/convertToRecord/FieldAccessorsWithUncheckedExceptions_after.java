record R(int first, int second, int third) {
    /**
     * @throws NullPointerException
     */
    R(int first, int second, int third) {
        this.first = first;
        this.second = second;
        this.third = third;
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