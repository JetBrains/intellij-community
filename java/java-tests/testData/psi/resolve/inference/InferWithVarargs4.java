class List<T> {}

class Test {
    <T> List<T> asList(T... ts) {
        return null;
    }
    void foo () {
        <ref>asList(new int[0], new int[0]);
    }
}
