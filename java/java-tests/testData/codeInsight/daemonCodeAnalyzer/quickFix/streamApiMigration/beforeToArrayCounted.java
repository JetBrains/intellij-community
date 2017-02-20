// "Replace with toArray" "true"

import java.util.Arrays;
import java.util.List;

public class Test {
  public void test(List<Integer> ints) {
    long[] arr = new long[ints.size()];
    for(int <caret>i = 0; i < ints.size(); i++) {
      arr[i] = ints.get(i);
    }
    System.out.println(Arrays.toString(arr));
  }
}
