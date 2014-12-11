@FunctionalInterface
interface I<T> {
  void foo(T t);
}

class Foo {
    void test() {
      <selection>String::toLowerCase</selection>;
    }
}