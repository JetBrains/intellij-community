import java.util.function.Consumer;

class Test {
  String myName;
  void foo() {
    if (true) {
        Consumer<String> stringConsumer = new Consumer<String>() {
            public void accept(String myName) {
                System.out.println("Hello, world " + myName);
            }
        };
        stringConsumer.accept(myName);
    }
  }
}