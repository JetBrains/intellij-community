interface I {
    void m(int x);
}

class Test {
    void foo(Object x) {}

    void bar() {
        foo(!<error descr="Lambda expression not expected here">(int x)-> {}</error>);
        foo(<error descr="Lambda expression not expected here">(int x)-> { }</error> instanceof Object );
    }

    I bazz() {
        foo((I)(int x)-> { });
        I o = (I)(int x)-> { };
        return (int x) -> {};
    }
}