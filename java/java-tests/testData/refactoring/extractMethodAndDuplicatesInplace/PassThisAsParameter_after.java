public class Test<K> {
    public Test<K> test() {
        return getkTest(this);
    }

    private static <K> Test<K> getkTest(Test<K> test) {
        return test;
    }
}