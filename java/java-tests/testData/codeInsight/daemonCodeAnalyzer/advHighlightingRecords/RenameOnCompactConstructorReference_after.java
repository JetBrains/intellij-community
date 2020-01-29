interface IBar {
    int baz();
}
record Foo(int baz) implements IBar{
    public Foo { this.baz = b<caret>az; }
    public int baz() { return baz; }
    public int test() { return baz(); }
}