class Neg10 {
    static class Foo<X> {
        Foo(X x) {}
    }

    <error descr="Incompatible types. Found: 'Neg10.Foo<java.lang.Integer>', required: 'Neg10.Foo<java.lang.Number>'">Foo<Number> fw = new Foo<>(1);</error>
}