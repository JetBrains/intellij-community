// "Replace with enhanced 'switch' statement" "true"

class A {
  public static void test13() {
    record R1(){}
    record R2(){}
    Object obj = "Hello";
      switch (obj) {
          case R1() -> System.out.println();
          case R2() -> System.out.println();
          default -> {
          }
      }

  }
}