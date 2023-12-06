// "Replace with enhanced 'switch' statement" "true-preview"

class A {
  private static void testSwitch2(int c) {
      switch (c) {
          case -2 -> {
          }
          case -1, 0 -> {
          }
          case 1, 2 -> System.out.println(" > 0");
          default -> {
          }
      }
  }
}