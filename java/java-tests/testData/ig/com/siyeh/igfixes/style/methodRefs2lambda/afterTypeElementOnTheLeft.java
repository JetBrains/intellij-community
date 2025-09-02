// "Replace method reference with lambda" "true-preview"
class Test<T> {
  static void foo() {}
}

class Bar {
  void test() {
    Runnable runnable = () -> Test.foo();
  }
}