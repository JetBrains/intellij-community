@FunctionalInterface
interface I<T> {
  void foo(T t);
}

class Foo {
    void test() {
        I<String> l = String::toLowerCase;
    }
}