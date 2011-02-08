class Foo {
    <T> Foo<T> f() {
    }

    static {
      new Foo().<Integer><ref>f();
    }
}