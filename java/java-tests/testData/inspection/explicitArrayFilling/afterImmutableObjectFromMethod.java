// "Replace loop with 'Arrays.fill()' method call" "true"

import java.util.Arrays;

class Test {

  void test() {
    final String[] arr = new String[2];
      Arrays.fill(arr, getString());
  }

  private static String getString() {
    return "foo";
  }
}