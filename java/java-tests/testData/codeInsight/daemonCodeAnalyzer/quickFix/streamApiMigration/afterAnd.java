// "Replace with reduce()" "true"

import java.util.*;

public class Main {
  public void testBitwiseAnd() {
    int[] arr = new int[]{1, 2, 3, 4};
    int acc = 12443;
      acc &= Arrays.stream(arr).reduce(-1, (a, b) -> a & b);
  }
}