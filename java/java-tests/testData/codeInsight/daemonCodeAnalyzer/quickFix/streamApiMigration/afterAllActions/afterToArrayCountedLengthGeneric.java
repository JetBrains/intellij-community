// "Replace with toArray" "true"

import java.util.Arrays;
import java.util.List;

public class Test {
  public void test(List<List<String>> list) {
      List<?>[] arr = list.toArray(new List[0]);
      System.out.println(Arrays.toString(arr));
  }
}
