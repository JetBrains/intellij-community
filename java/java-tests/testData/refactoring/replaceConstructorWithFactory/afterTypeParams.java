class Test<A,B,C> {
    private Test() {
    }

    static <A, B, C> Test<A, B, C> createTest() {
        return new Test<A, B, C>();
    }
}