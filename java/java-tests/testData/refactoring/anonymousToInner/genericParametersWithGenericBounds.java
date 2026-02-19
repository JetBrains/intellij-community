class Test<T> {
    <U extends T, V extends Comparable<U>> void test() {
        class <caret>Inner {
            V foo() {
                return null;
            }
        }
        new Inner();
    }
}
