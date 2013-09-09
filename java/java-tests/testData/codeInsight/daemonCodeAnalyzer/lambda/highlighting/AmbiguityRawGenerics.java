interface I {
    void m();
}

interface I1 {
    int m();
}

interface I2 {
    String m();
}

interface I3<A> {
    A m();
}

class AmbiguityRawGenerics {

    void foo(I s) { }
    void foo(I1 s) { }
    void foo(I2 s) { }
    <Z> void foo(I3<Z> s) { }

    void bar() {
        foo<error descr="Ambiguous method call: both 'AmbiguityRawGenerics.foo(I)' and 'AmbiguityRawGenerics.foo(I3<Object>)' match">(()-> { throw new RuntimeException(); })</error>;
    }
}