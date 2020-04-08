record Foo(int baz) {
    public Foo { this.baz = baz; }
    public int b<caret>az() { return baz; }
    public int test() { return baz(); }
}