class Test {
  void foo() {
      final Foo<Number> a = new Foo<Number>(1);
  }
}

class Foo<T> {
  Foo(T t) {
  }
}