class Foo {
    void bar() {}
    void foo() {}
}

class Bar extends Foo {
    void foo() {
        super.<caret>
    }
}