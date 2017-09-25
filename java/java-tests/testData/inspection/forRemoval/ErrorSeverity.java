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
    Test.<error descr="'Test.Foo' is deprecated and marked for removal">Foo</error> f =
      new Test.<error descr="'Test.Foo' is deprecated and marked for removal">Foo</error>();
    int i = t.<error descr="'foo' is deprecated and marked for removal">foo</error>;
    t.<error descr="'foo()' is deprecated and marked for removal">foo</error>();
  }
}
