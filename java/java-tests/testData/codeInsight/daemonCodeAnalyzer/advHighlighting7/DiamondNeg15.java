class Pos8 {

    static class Foo<X> {
        Foo(X t) {}
    }

    Foo<Integer> fi1 = new Foo<>(1);
    Foo<Integer> fi2 = new Foo<Integer>(1);
    Foo<Integer> fi3 = new <String> Foo<<error descr="Cannot use diamonds with explicit type parameters for constructor"></error>>(1);
    Foo<Integer> fi4 = new <String> Foo<Integer>(1);
    Foo<Integer> fi5 = new <String, String> Foo<<error descr="Cannot use diamonds with explicit type parameters for constructor"></error>>(1);
    Foo<Integer> fi6 = new <String, String> Foo<Integer>(1);
}
