record Foo(int b<caret>ar, int foo) {
    public Foo(int bar, int foo) {
        this.bar = bar;
        this.foo = foo;
    }

    Foo(int bar) {
        this(bar, 0);
    }
}