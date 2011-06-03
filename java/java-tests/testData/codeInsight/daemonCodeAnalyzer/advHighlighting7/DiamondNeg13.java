class Neg13 {

    static class Foo<X> {
        <T> Foo(T t) {}
    }

    Foo<Integer> fi1 = new <String, Integer> Foo<<error descr="Cannot use diamonds with explicit type parameters for constructor"></error>>("");
    Foo<Integer> fi2 = new <error descr="Wrong number of type arguments: 2; required: 1"><String, Integer></error> Foo<Integer>("");
}
