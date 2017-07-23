// "Replace with toArray" "true"

import java.util.Arrays;
import java.util.stream.IntStream;

public class Test {
  public void test(int bound) {
      Integer[][] arr = IntStream.range(0, bound).mapToObj(i -> new Integer[]{i}).toArray(Integer[][]::new);
      System.out.println(Arrays.toString(arr));
  }
}
