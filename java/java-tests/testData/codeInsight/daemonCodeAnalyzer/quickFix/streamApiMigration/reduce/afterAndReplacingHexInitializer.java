// "Collapse loop with stream 'reduce()'" "true-preview"

import java.util.*;

public class Main {
  public void testBitwiseAndReplacingInitializer() {
    int[] arr = new int[]{1, 2, 3, 4};
    int acc = Arrays.stream(arr).reduce(0xFFFFFFFF, (a, b) -> a & b);
  }
}