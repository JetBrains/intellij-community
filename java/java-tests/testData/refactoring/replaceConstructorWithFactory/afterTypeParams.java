class Test<A,B,C> {
    private Test() {
    }

    static <A, B, C> Test<A, B, C> newTest() {
        return new Test<A, B, C>();
    }
}