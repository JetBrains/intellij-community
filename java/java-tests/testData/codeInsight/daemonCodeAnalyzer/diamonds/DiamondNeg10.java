class Neg10 {
    static class Foo<X> {
        Foo(X x) {}
    }

    Foo<Number> fw = new <error descr="Incompatible types. Found: 'Neg10.Foo<java.lang.Integer>', required: 'Neg10.Foo<java.lang.Number>'">Foo<></error>(1);
}