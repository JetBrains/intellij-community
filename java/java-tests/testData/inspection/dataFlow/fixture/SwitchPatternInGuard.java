import org.jetbrains.annotations.NotNull;

class Test {
  sealed interface I {}
  record A(String a) implements I {}

  public static void main(String[] args) {
    I i = createI();
    switch (i) {
      case A(var a) when a instanceof String s -> System.out.println(s);
      default -> System.out.println("default");
    }
  }

  private static @NotNull A createI() {
    return new A("1");
  }
}