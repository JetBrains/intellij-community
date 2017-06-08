import java.util.function.Supplier;

class Test {
    void foo() {
    if (true) {
        Supplier<String> stringSupplier = () -> "Hello, world";
        System.out.println(stringSupplier.get());
    }
  }
}