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
        <error descr="Ambiguous method call: both 'AmbiguityRawGenerics.foo(I1)' and 'AmbiguityRawGenerics.foo(I2)' match">foo</error>(()-> { throw new RuntimeException(); });
    }
}