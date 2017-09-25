class Test {
  @Deprecated(forRemoval = true)
  static class Foo { }

  @Deprecated(forRemoval = true)
  int foo;

  @Deprecated(forRemoval = true)
  void foo() {}

  void bar() {
    Foo f = new Foo();
    int i = foo;
    foo();
  }
}

class Usages {
  void bar() {
    Test t = new Test();
    Test.<warning descr="'Test.Foo' is deprecated and marked for removal(LIKE_DEPRECATED)">Foo</warning> f =
      new Test.<warning descr="'Test.Foo' is deprecated and marked for removal(LIKE_DEPRECATED)">Foo</warning>();
    int i = t.<warning descr="'foo' is deprecated and marked for removal(LIKE_DEPRECATED)">foo</warning>;
    t.<warning descr="'foo()' is deprecated and marked for removal(LIKE_DEPRECATED)">foo</warning>();
  }
}
