// "Replace with toArray" "true"

import java.util.Arrays;
import java.util.List;

public class Test {
  public void test(List<Integer> ints) {
      long[] arr = ints.stream().mapToLong(anInt -> anInt).toArray();
      System.out.println(Arrays.toString(arr));
  }
}
