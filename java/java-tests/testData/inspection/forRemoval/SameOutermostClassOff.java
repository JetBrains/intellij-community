class Test {
  static class Nested {
    @Deprecated(forRemoval = true)
    class Foo { }

    @Deprecated(forRemoval = true)
    int foo;

    @Deprecated(forRemoval = true)
    void foo() {}
  }

  void bar() {
    Nested n = new Nested();
    Nested.<error descr="'Test.Nested.Foo' is deprecated and marked for removal">Foo</error> f =
      n.new <error descr="'Test.Nested.Foo' is deprecated and marked for removal">Foo</error>();
    int i = n.<error descr="'foo' is deprecated and marked for removal">foo</error>;
    n.<error descr="'foo()' is deprecated and marked for removal">foo</error>();
  }

  class NestedUsages {
    void bar() {
      Nested n = new Nested();
      Nested.<error descr="'Test.Nested.Foo' is deprecated and marked for removal">Foo</error> f =
        n.new <error descr="'Test.Nested.Foo' is deprecated and marked for removal">Foo</error>();
      int i = n.<error descr="'foo' is deprecated and marked for removal">foo</error>;
      n.<error descr="'foo()' is deprecated and marked for removal">foo</error>();
    }
  }
}

class Usages {
  void bar() {
    Test.Nested n = new Test.Nested();
    Test.Nested.<error descr="'Test.Nested.Foo' is deprecated and marked for removal">Foo</error> f =
      n.new <error descr="'Test.Nested.Foo' is deprecated and marked for removal">Foo</error>();
    int i = n.<error descr="'foo' is deprecated and marked for removal">foo</error>;
    n.<error descr="'foo()' is deprecated and marked for removal">foo</error>();
  }
}
