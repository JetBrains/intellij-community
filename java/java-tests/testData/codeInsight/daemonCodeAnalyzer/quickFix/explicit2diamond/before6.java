// "Replace with <>" "false"
class Test {

    void test() {
        class Foo<X extends Number> {
            Foo() {}
            Foo(X x) {}
        }
        Foo<Number> f1 = new Foo<Number>(1);
        Foo<?> f2 = new Foo<Number>();
        Foo<?> f3 = new Foo<Inte<caret>ger>();
        Foo<Number> f4 = new Foo<Number>(1) {};
        Foo<?> f5 = new Foo<Number>() {};
        Foo<?> f6 = new Foo<Integer>() {};
    }
}
