// "Replace loop with 'Arrays.fill()' method call" "true"

import java.util.Arrays;

class Test {
  private void test2() {
    int[][] arr = new int[10][];
      Arrays.fill(arr, new int[0]);
  }
}