@FunctionalInterface
interface D<T> {
  void accept(T t);
}


class Foo {
    void test() {
        D<Integer> l = System::exit;
    }
}