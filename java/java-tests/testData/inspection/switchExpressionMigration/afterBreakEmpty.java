// "Replace with enhanced 'switch' statement" "true-preview"

class A {
  private static void testSwitch(int c) {
      switch (c) {
          case -1, 0 -> {
          }
          case 1, 2 -> System.out.println(" > 0");
          default -> System.out.println(" > 2");
      }
  }
}