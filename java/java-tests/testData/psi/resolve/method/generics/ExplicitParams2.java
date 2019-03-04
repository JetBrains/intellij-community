class Foo {
    <T> Foo<T> f() {
    }

    static {
      new Foo().<Integer><caret>f();
    }
}