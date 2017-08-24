class Foo {
    void m(Runnable r) {}
    void ff(Runnable r) {
        m(r);
    }
    {
        f<caret>f(() -> {});
    }
}