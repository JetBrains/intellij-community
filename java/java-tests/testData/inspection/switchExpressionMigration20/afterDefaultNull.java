// "Replace with enhanced 'switch' statement" "true"

class A {
  private static void testSwitch(Integer c) {
      switch (c) {
          case 1, 2 -> System.out.println(" > 0");
          case null, default -> System.out.println(" > 2");
      }
  }
}