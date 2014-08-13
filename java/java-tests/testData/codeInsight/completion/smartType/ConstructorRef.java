@FunctionalInterface
interface Foo9 {
  Bar test(int p);
}

class Bar {
  public Bar(int p) {}
}

class Test88 {
  void foo(Foo9 foo) {
    foo(Bar::<caret>);
  }
}