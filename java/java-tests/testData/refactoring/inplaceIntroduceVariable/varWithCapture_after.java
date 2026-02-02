class Foo<T> {
  interface Bar {

  }

  void test() {
      var foo = getFoo();
  }

  native Foo<? extends Bar> getFoo();
}