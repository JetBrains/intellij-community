class Test {
    static class SuperFoo<X> {}

    static class Foo<X extends Number> extends SuperFoo<X> {}

    interface I<Y> {
        SuperFoo<Y> m();
    }

    <R> SuperFoo<R> foo(I<R> ax) { return null; }

    SuperFoo<String> ls = foo(<error descr="no instance(s) of type variable(s)  exist so that String conforms to Number
inference variable R has incompatible bounds:
 equality constraints: String
upper bounds: Object, Number
inference variable X has incompatible bounds:
 equality constraints: R
upper bounds: Number">() -> new Foo<>()</error>);
    SuperFoo<Integer> li = foo(() -> new Foo<>());
    SuperFoo<?> lw = foo(() -> new Foo<>());
}