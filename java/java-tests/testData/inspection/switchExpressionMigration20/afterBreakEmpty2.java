// "Replace with enhanced 'switch' statement" "true"

class A {
  private static void testSwitch2(Integer c) {
      switch (c) {
          case null -> {
          }
          case -1 -> {
          }
          case 0 -> {
          }
          case 1, 2 -> System.out.println(" > 0");
          default -> {
          }
      }
  }
}