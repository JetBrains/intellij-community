@FunctionalInterface
interface D<T> {
  void accept(T t);
}


class Foo {
    void test() {
      <selection>System::exit</selection>;
    }
}