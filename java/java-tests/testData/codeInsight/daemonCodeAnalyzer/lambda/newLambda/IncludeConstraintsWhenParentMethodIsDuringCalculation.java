class Test {
    static class SuperFoo<X> {}

    static class Foo<X extends Number> extends SuperFoo<X> {}

    interface I<Y> {
        SuperFoo<Y> m();
    }

    <R> SuperFoo<R> foo(I<R> ax) { return null; }

    SuperFoo<String> ls = <error descr="Incompatible types. Found: 'Test.SuperFoo<java.lang.Number>', required: 'Test.SuperFoo<java.lang.String>'">foo</error>(() -> new Foo<>());
    SuperFoo<Integer> li = foo(() -> new Foo<>());
    SuperFoo<?> lw = foo(() -> new Foo<>());
}