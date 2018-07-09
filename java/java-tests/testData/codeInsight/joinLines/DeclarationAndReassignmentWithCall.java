class Foo {
    interface X {
        X getParent();
    }

    void test(X x1, X x2, boolean b) {
        <caret>X x = b ? x1 : x2;
        x = x.getParent();
    }
}