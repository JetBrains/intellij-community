class Test {
  sealed interface I {}
  record A(String a) implements I {}

  public static void main(String[] args) {
    I i = new A("1");
    switch (i) {
      case A(var a) when a instanceof String s -> System.out.println(s);
      default -> System.out.println("default");
    }
  }
}