class Foo {
    interface X {
        X getParent();
    }

    void test(X x) {
        <caret>x = x.getParent();
        x = x.getParent();
    }
}