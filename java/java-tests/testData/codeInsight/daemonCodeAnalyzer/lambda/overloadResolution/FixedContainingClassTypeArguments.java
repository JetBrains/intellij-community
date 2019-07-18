class MyTest {

    static class Foo<X> {
        <T> void test(X x) { }
    }
    static class Bar extends Foo<Integer> {
        void test(Double x) { }

        void call() {
            test<error descr="Ambiguous method call: both 'Bar.test(Double)' and 'Foo.test(Integer)' match">(null)</error>;
        }
    }
}