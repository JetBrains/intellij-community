class Test {
  void foo() {
      new Foo<Num<caret>ber>(1);
  }
}

class Foo<T> {
  Foo(T t) {
  }
}