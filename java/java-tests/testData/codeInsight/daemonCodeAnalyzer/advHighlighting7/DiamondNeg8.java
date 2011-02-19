class Neg08 {
    static class Foo<X> {
        Foo(X x) {  }
    }

    static class DoubleFoo<X,Y> {
        DoubleFoo(X x,Y y) {  }
    }

    static class TripleFoo<X,Y,Z> {
        TripleFoo(X x,Y y,Z z) {  }
    }

    Foo<? extends Integer> fi = new Foo<>(1);
    Foo<?> fw = new Foo<<error descr="Cannot infer type arguments for Foo<> because type Foo<? extends Integer> inferred is not allowed in current context"></error>>(fi);    
    Foo<? extends Double> fd = new Foo<>(3.0);
    DoubleFoo<?,?> dw = new DoubleFoo<<error descr="Cannot infer type arguments for DoubleFoo<> because type Foo<? extends Integer> inferred is not allowed in current context"></error>>(fi,fd);
    Foo<String> fs = new Foo<>("one");
    TripleFoo<?,?,?> tw = new TripleFoo<<error descr="Cannot infer type arguments for TripleFoo<> because type Foo<? extends Integer> inferred is not allowed in current context"></error>>(fi,fd,fs); 
}
