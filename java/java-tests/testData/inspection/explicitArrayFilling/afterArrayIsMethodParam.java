// "Replace loop with 'Arrays.fill()' method call" "true"

import java.util.Arrays;

public class Test {

  public static int[] init(int[] arr, boolean b) {
    if (b) {
      arr = new int[10];
    }
      Arrays.fill(arr, 0);
    return arr;
  }
}