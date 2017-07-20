// "Replace with reduce()" "true"

import java.util.*;

public class Main {
  public void testBitwiseAndReplacingLongInitializer() {
    long[] arr = new long[]{1, 2, 3, 4};
      long acc = Arrays.stream(arr).reduce(-1l, (a, b) -> a & b);
  }
}