// "Replace with toArray" "true"

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

public class Test {
  public void test(List<List<String>> list) {
      List<?>[] arr = IntStream.range(0, list.size()).mapToObj(list::get).toArray(List[]::new);
      System.out.println(Arrays.toString(arr));
  }
}
