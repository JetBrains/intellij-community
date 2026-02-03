class Foo {
    String fooLongButOfDefaultType() {}
    Foo foo() {}
}

class Bar {
    void foo() {
        Foo f;
        if ("abc".equals(f.foo<caret>))
    }
}