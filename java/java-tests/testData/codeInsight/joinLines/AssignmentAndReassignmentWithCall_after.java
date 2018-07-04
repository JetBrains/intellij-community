class Foo {
    interface X {
        X getParent();
    }

    void test(X x) {
        x = x.getParent().getParent();<caret>
    }
}