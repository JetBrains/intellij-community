import java.util.function.Supplier;

class Test {
  void foo() {
      Supplier<String> stringSupplier = new Supplier<String>() {
          public String get() {
              return "Hello, world";
          }
      };
      System.out.println(stringSupplier.get());
  }
}