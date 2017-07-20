// "Replace with reduce()" "true"

import java.util.*;

public class Main {
  public void testMultiplyWithNarrowingTypeInitializer() {
    int[] arr = new int[]{1, 2, 3, 4};
    short acc = (byte) 12;
      acc *= Arrays.stream(arr).map(i -> (short) i).reduce(1, (a, b) -> a * b);
  }
}