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
    Test.<weak_warning descr="'Test.Foo' is deprecated and marked for removal(LIKE_DEPRECATED)">Foo</weak_warning> f =
      new Test.<weak_warning descr="'Test.Foo' is deprecated and marked for removal(LIKE_DEPRECATED)">Foo</weak_warning>();
    int i = t.<weak_warning descr="'foo' is deprecated and marked for removal(LIKE_DEPRECATED)">foo</weak_warning>;
    t.<weak_warning descr="'foo()' is deprecated and marked for removal(LIKE_DEPRECATED)">foo</weak_warning>();
  }
}
