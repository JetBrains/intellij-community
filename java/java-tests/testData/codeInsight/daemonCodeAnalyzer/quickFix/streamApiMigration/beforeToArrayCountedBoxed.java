// "Replace with toArray" "true"

import java.util.Arrays;

public class Test {
  public void test(int bound) {
    Object[] arr = new Integer[bound];
    for(int <caret>i = 0; i < bound; i++) {
      arr[i] = i;
    }
    System.out.println(Arrays.toString(arr));
  }
}
