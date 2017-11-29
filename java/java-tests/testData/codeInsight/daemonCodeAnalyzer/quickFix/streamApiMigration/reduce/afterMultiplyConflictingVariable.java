// "Replace with reduce()" "true"

import java.util.*;

public class Main {
  public void testMultiplyWithConflictingNamesInScope() {
    int a = 1;
    int b = 2;
    int b1 = 3;
    int[] arr = new int[]{1, 2, 3, 4};
      int acc = Arrays.stream(arr).reduce(1, (a1, b2) -> a1 * b2);
  }
}