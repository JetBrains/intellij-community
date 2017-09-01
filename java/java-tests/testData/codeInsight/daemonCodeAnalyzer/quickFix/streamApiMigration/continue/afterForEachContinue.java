// "Replace with forEach" "true"

import java.util.Arrays;
import java.util.Collection;

public class Test {
  void test(int[] arr) {
      Arrays.stream(arr).forEach(x -> {
          int y = x * 2;
          if (x > y) return;
          System.out.println(x);
      });
  }
}
