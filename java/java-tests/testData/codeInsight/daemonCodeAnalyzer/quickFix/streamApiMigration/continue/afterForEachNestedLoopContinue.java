// "Replace with forEach" "true"

import java.util.Arrays;
import java.util.Collection;

public class Test {
  void test(int[] arr, int[] arr2) {
      Arrays.stream(arr).forEach(x -> {
          int y = x * 2;
          if (x > y) return; // comment
          for (int i : arr2) {
              if (i % 2 == 0) continue;
              System.out.println(x);
          }
      });
  }
}
