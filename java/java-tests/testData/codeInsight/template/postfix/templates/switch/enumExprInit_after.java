public enum Foo {
    A, B, C;

    void f() {
        Foo foo = switch (Foo.values()[0]) {
            <caret>
        }
    }
}