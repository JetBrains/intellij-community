// "Replace with enhanced 'switch' statement" "true"

class A {
  private static void testSwitch(Integer c) {
      switch (c) {
          case -1 -> {
          }
          case null -> {
          }
          case 1, 2 -> System.out.println(" > 0");
          default -> System.out.println(" > 2");
      }
  }
}