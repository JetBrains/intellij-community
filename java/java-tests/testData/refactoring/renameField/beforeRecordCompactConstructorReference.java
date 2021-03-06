interface IBar {
    int bar();
}
record Foo(int bar) implements IBar{
    public Foo { this.bar = b<caret>ar; }
    public int bar() { return bar; }
    public int test() { return bar(); }
}