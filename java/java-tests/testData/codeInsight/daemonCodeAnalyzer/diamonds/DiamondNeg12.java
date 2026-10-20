class Neg12 {

    static class Foo<X> {
        <T> Foo(T t) {}
    }

    Foo<Integer> fi1 = new <String> Foo<error descr="Cannot use diamonds with explicit type parameters for constructor"><></error>(1);
    Foo<Integer> fi2 = new <String> Foo<Integer><error descr="'Foo(java.lang.String)' in 'Neg12.Foo' cannot be applied to '(int)'">(1)</error>;
    Foo<Integer> fi3 = new Foo<Integer>(1);
}
