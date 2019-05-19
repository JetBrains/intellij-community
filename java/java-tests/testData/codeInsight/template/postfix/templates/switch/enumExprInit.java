public enum Foo {
    A, B, C;

    void f() {
        Foo foo = Foo.values()[0].switch<caret>
    }
}