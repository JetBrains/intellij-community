interface I {
    void m(int x);
}

class Test {
    void foo(Object x) {}

    void bar() {
        foo(<error descr="Operator '!' cannot be applied to '<lambda expression>'">!(int x)-> {}</error>);
        foo(<error descr="Lambda expression is not expected here">(int x)-> { } instanceof Object</error> );
    }

    I bazz() {
        foo((I)(int x)-> { });
        I o = (I)(int x)-> { };
        return (int x) -> {};
    }
}