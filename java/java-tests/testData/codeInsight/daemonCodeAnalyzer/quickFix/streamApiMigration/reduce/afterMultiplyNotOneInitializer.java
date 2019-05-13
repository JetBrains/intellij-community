// "Replace with reduce()" "true"

import java.util.*;

public class Main {
  public void testMultiplyWithNotOneInitializerConstant() {
    int[] arr = new int[]{1, 2, 3, 4};
    int acc = 12;
      acc *= Arrays.stream(arr).reduce(1, (a, b) -> a * b);
  }
}