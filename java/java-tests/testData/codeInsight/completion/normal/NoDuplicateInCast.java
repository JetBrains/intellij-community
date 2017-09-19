class X {
  void foo(Object o){
    if (o instanceof Foo) {
      ((Foo<caret>) o).bar();
    }
  }

  class Foo {
    void bar() {}
  }
}