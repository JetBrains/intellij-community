class List<T> {}

class Test {
    <T> List<T> asList(T... ts) {
        return null;
    }
    void foo () {
        <ref>asList(new Integer[0], new Integer[0]);
    }
}
