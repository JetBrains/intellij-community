class Neg14 {

    static class Foo<X> {
        <T extends Integer> Foo(T t) {}
    }

    Foo<Integer> fi1 = new <String> Foo<error descr="Cannot use diamonds with explicit type parameters for constructor"><></error>(1);
    Foo<Integer> fi2 = new <<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Integer'">String</error>> Foo<Integer>(1);
}
