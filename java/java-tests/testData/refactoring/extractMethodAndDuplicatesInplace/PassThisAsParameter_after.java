public class Test<K> {
    public Test<K> test() {
        return getKTest(this);
    }

    private static <K> Test<K> getKTest(Test<K> test) {
        return test;
    }
}