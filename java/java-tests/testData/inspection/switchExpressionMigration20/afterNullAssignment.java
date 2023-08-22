// "Replace with enhanced 'switch' statement" "true"

class A {
  private static void testSwitch2(Integer c) {
    int i;
      switch (c) {
          case null -> i = 1;
          case -1 -> i = 1;
          case 0 -> i = 0;
          case 1, 2 -> i = 3;
          default -> i = 5;
      }
  }
}