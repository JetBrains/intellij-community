class Test {
    static class SuperFoo<X> {}

    static class Foo<X extends Number> extends SuperFoo<X> {}

    interface I<Y> {
        SuperFoo<Y> m();
    }

    <R> SuperFoo<R> foo(I<R> ax) { return null; }

    SuperFoo<String> ls = foo(() -> new Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>());
    SuperFoo<Integer> li = foo(() -> new Foo<>());
    SuperFoo<?> lw = foo(() -> new Foo<>());
}