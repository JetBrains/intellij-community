class Foo {
    void foo(String... s) {}
}

class Bar {
    Foo myDelegate;

    public void foo(String... s) {
        myDelegate.foo(s);
    }
}