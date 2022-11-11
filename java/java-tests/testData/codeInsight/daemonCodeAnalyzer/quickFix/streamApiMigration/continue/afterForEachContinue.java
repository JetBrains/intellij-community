// "Replace with forEach" "true-preview"

import java.util.Arrays;
import java.util.Collection;

public class Test {
  void test(int[] arr) {
      Arrays.stream(arr).forEach(x -> {
          int y = x * 2;
          if (x > y) return; // comment
          System.out.println(x);
      });
  }
}
