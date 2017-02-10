// "Replace with toArray" "true"

import java.util.Arrays;

public class Test {
  public void test(int bound) {
    Integer[][] arr = new Integer[bound][];
    for(int <caret>i = 0; i < arr.length; i++) {
      arr[i] = new Integer[] {i};
    }
    System.out.println(Arrays.toString(arr));
  }
}
