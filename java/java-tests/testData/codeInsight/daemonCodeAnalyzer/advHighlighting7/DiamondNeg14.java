class Neg14 {

    static class Foo<X> {
        <T extends Integer> Foo(T t) {}
    }

    Foo<Integer> fi1 = new <String> Foo<<error descr="Cannot use diamonds with explicit type parameters for constructor"></error>>(1);
    Foo<Integer> fi2 = new <<error descr="Actual type argument and inferred type contradict each other">String</error>> Foo<Integer>(1);
}
