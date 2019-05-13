class Super {
    void f<caret>oo() {}
}

class Child extends Super {
    {
        foo();
    }

    @Override
    void foo() {
        super.foo();
    }
}