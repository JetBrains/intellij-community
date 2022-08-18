// "Replace with reduce()" "true-preview"

import java.util.*;

public class Main {
  public void testBitwiseAndReplacingLongInitializer() {
    long[] arr = new long[]{1, 2, 3, 4};
    long acc = -1l;
    for <caret> (long i: arr) {
      acc &= i;
    }
  }
}