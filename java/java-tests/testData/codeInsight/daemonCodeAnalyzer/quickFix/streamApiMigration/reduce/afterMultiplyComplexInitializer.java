// "Collapse loop with stream 'reduce()'" "true-preview"

import java.util.*;

public class Main {
  public void testMultiplyWithComplexInitializer() {
    int[] arr = new int[]{1, 2, 3, 4};
    int x = 10;
    int acc = 12 + x;
      acc *= Arrays.stream(arr).reduce(1, (a, b) -> a * b);
  }
}