class Foo {
  boolean foo;
  boolean bar;

  {
    foo(f<caret>)
  }

  void foo(boolean foo, boolean bar) {
  }

}