import java.util.function.Supplier;

class Test {
    void foo() {
        Supplier<String> supplier = new Supplier<String>() {
            public String get() {
                return "Hello, world";
            }
        };
        System.out.println(supplier.get());
  }
}