// "Replace with toArray" "true"

import java.util.Arrays;
import java.util.List;

public class Test {
  public void test(List<List<String>> list) {
    List<?>[] arr = new List[list.size()];
    for(i<caret>nt i = 0; i < arr.length; i++) {
      arr[i] = list.get(i);
    }
    System.out.println(Arrays.toString(arr));
  }
}
