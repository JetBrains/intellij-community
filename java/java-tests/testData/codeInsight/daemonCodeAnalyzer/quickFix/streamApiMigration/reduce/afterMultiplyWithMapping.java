// "Replace with reduce()" "true"

import java.util.*;

public class Main {
  public void testMultiplyWithMapping() {
    int[] arr = new int[]{1, 2, 3, 4};
      int acc = Arrays.stream(arr).map(i -> i + 2).reduce(1, (a, b) -> a * b);
  }
}