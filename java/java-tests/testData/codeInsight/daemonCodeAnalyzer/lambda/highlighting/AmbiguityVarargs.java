interface I {
    void m();
}

interface I1<A> {
    void m(A a);
}

interface I2<A> {
    void m(A a1, A a2);
}

interface IV<A> {
    void m(A... as);
}

class AmbiguityVarargs {
    void foo(I s) { }
    void foo(I1<String> s) { }
    void foo(I2<String> s) { }
    void foo(IV<String> s) { }

    void test() {
        foo(()->{});
        <error descr="Ambiguous method call: both 'AmbiguityVarargs.foo(I1<String>)' and 'AmbiguityVarargs.foo(IV<String>)' match">foo</error>((a1) -> {});
        foo((a1, a2)->{});
    }
}