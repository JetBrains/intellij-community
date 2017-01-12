// "Replace with toArray" "true"

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

public class Test {
  public void test(List<Integer> ints) {
      long[] arr = IntStream.range(0, ints.size()).mapToLong(ints::get).toArray();
      System.out.println(Arrays.toString(arr));
  }
}
