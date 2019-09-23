// "Replace loop with 'Arrays.fill()' method call" "true"

import java.util.Arrays;

public class Test {

  public static int[] init(boolean b) {
    int[] arr = new int[10];
    if (b) {
      arr = new int[]{1, 2, 3, 4, 5};
    }
      Arrays.fill(arr, 0);
    return arr;
  }
}