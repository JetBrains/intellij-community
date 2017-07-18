// "Replace with reduce()" "true"

import java.util.*;

public class Main {
  public void testMultiplyWithLowerTypeInitializer() {
    int[] arr = new int[]{1, 2, 3, 4};
      short acc = (short) ((byte) 12 * Arrays.stream(arr).reduce(1, (a, b) -> a * b));
  }
}