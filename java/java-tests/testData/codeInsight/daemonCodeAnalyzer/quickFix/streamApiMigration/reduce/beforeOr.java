// "Collapse loop with stream 'reduce()'" "true-preview"

import java.util.*;

public class Main {
  public void testBitwiseOr() {
    int[] arr = new int[]{1, 2, 3, 4};
    int acc = 12443;
    for <caret> (int i: arr) {
      acc |= i;
    }
  }
}