class Foo<T> {
  interface Bar {

  }

  void test() {
    get<caret>Foo();
  }

  native Foo<? extends Bar> getFoo();
}