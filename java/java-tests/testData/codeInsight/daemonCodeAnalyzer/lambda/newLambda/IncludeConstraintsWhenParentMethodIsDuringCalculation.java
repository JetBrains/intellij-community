class Test {
    static class SuperFoo<X> {}

    static class Foo<X extends Number> extends SuperFoo<X> {}

    interface I<Y> {
        SuperFoo<Y> m();
    }

    <R> SuperFoo<R> foo(I<R> ax) { return null; }

    SuperFoo<String> ls = foo(<error descr="Incompatible types. Required SuperFoo<String> but 'foo' was inferred to SuperFoo<R>:
no instance(s) of type variable(s)  exist so that String conforms to Number">() -> new Foo<>()</error>);
    SuperFoo<Integer> li = foo(() -> new Foo<>());
    SuperFoo<?> lw = foo(() -> new Foo<>());
}