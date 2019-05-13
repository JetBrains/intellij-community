// "Replace with reduce()" "true"

import java.util.*;

public class Main {
  public void testBitwiseOr() {
    int[] arr = new int[]{1, 2, 3, 4};
    int acc = 12443;
      acc |= Arrays.stream(arr).reduce(0, (a, b) -> a | b);
  }
}