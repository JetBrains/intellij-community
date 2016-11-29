interface MyIntf {}

    void foo(MyIntf i){}
    void foo(Object[] array){}

    void test() {
        foo(new MyIntf<caret>)
    }
