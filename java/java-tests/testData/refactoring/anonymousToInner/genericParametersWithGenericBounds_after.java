class Test<T> {
    <U extends T, V extends Comparable<U>> void test() {
        new Inner<U, V>();
    }

    private class Inner<U extends T, V extends Comparable<U>> {
        V foo() {
            return null;
        }
    }
}
