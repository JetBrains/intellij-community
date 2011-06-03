class Pos07 {
    static class Foo<X extends Number & Comparable<Number>> {}
    static class DoubleFoo<X extends Number & Comparable<Number>,
                           Y extends Number & Comparable<Number>> {}
    static class TripleFoo<X extends Number & Comparable<Number>,
                           Y extends Number & Comparable<Number>,
                           Z> {}

    Foo<?> fw = new Foo<>();
    DoubleFoo<?,?> dw = new DoubleFoo<>();
    TripleFoo<?,?,?> tw = new TripleFoo<>();
}
