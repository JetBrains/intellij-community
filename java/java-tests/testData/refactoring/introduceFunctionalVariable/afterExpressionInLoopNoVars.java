import java.util.function.Supplier;

class Test {
    void foo() {
    if (true) {
        Supplier<String> supplier = new Supplier<String>() {
            public String get() {
                return "Hello, world";
            }
        };
        System.out.println(supplier.get());
    }
  }
}