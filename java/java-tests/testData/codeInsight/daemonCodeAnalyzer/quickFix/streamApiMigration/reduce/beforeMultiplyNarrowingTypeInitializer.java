// "Collapse loop with stream 'reduce()'" "true-preview"

import java.util.*;

public class Main {
  public void testMultiplyWithNarrowingTypeInitializer() {
    int[] arr = new int[]{1, 2, 3, 4};
    short acc = (byte) 12;
    for <caret> (int i : arr) {
      acc *= i;
    }
  }
}