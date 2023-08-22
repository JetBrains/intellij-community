record Foo(int b<caret>az, int foo) {
    public Foo(int baz, int foo) {
        this.baz = baz;
        this.foo = foo;
    }

    Foo(int bar) {
        this(bar, 0);
    }
}