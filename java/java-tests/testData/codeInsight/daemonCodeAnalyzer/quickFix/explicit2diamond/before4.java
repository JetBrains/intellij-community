// "Replace with <>" "true-preview"
class Test {

    void test() {
        class Foo<X extends Number> {
            Foo() {}
            Foo(X x) {}
        }
        Foo<Number> f1 = new Foo<Nu<caret>mber>(1);
        Foo<?> f2 = new Foo<Number>();
        Foo<?> f3 = new Foo<Integer>();
        Foo<Number> f4 = new Foo<Number>(1) {};
        Foo<?> f5 = new Foo<Number>() {};
        Foo<?> f6 = new Foo<Integer>() {};
    }
}
