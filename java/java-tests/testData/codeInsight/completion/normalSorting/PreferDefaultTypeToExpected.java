class Foo {
    String getName() {}
    Foo getNameIdentifier() {}
}

class Bar {
    void foo() {
       Foo f;
        if ("abc".equals(f.getN<caret>))
    }
}