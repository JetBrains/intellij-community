@FunctionalInterface
interface I<T> {
  T foo(T t);
}

class Foo {
    void test() {
        I<String> l = String::toLowerCase;
    }
}