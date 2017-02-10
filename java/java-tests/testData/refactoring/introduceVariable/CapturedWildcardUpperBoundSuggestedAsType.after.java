interface I {}

abstract class Test<T extends I> {
  void foo(Test<?> t) {
      I m = t.get();
  }

  abstract T get();
}