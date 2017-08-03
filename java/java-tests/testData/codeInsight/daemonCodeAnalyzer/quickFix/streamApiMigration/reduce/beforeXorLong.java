// "Replace with reduce()" "true"

import java.util.*;

public class Main {
  public void testBitwiseXorLong() {
    int[] arr = new int[]{1, 2, 3, 4};
    long acc = 12443;
    for <caret> (int i: arr) {
      acc ^= i;
    }
  }
}