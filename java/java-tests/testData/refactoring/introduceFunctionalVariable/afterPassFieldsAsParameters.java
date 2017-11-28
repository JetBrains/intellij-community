import java.util.function.Consumer;

class Test {
    String myName;
  void foo() {
    if (true) {
        Consumer<String> stringConsumer = myName -> System.out.println("Hello, world " + myName);
        stringConsumer.accept(myName);
    }
  }
}