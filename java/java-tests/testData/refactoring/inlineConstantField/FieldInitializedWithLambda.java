interface Consumer<T> {
  void accept(T t);
}

class Test {
  class X {
    final Consumer<String> myCon<caret>sumer;

    X() {
      myConsumer = s -> System.out.println(s);
    }

    void test() {
      myConsumer.accept("1");
    }
  }
}