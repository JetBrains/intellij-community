class Test {
    void test(Helper<Integer> helper) {
      staticFactory(helper, Integer[]::new);
    }

    <P_OUT> void staticFactory(Helper<P_OUT> helper,
                               IntFunction<P_OUT[]> generator){}

    class Helper<K> {}

    interface IntFunction<R> {
        R apply(int value);
    }
}
