interface I {}

abstract class Test<T extends I> {
  void foo(Test<?> t) {
    <selection>t.get()</selection>
  }

  abstract T get();
}