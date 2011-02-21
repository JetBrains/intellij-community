class Neg09 {
    static class Foo<X extends Number & Comparable<Number>> {}
    static class DoubleFoo<X extends Number & Comparable<Number>,
                           Y extends Number & Comparable<Number>> {}
    static class TripleFoo<X extends Number & Comparable<Number>,
                           Y extends Number & Comparable<Number>,
                           Z> {}

    Foo<?> fw = new Foo<<error descr="Cannot infer type arguments for Foo<> because type ? extends Number inferred is not allowed in current context"></error>>();                  
    DoubleFoo<?,?> dw = new DoubleFoo<<error descr="Cannot infer type arguments for DoubleFoo<> because type ? extends Number inferred is not allowed in current context"></error>>();            
    TripleFoo<?,?,?> tw = new TripleFoo<<error descr="Cannot infer type arguments for TripleFoo<> because type ? extends Number inferred is not allowed in current context"></error>>();                  
}
