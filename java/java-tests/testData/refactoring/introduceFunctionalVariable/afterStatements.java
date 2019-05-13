import java.util.function.Consumer;

class Test {
    void foo(String s) {
    if (true) {
        Consumer<String> stringConsumer = s1 -> {
            System.out.println("Hello, world " + s1);
            System.out.println();
        };
        stringConsumer.accept(s);

        System.out.println();
    }
  }
}