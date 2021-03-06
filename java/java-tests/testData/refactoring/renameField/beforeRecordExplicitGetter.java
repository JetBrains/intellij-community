record Foo(int bar) {
    public Foo { this.bar = bar; }
    public int b<caret>ar() { return bar; }
    public int test() { return bar(); }
}