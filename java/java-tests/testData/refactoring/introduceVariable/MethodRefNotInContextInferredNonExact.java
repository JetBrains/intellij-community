@FunctionalInterface
interface I<T> {
  T foo(T t);
}

class Foo {
    void test() {
      <selection>String::toLowerCase</selection>;
    }
}