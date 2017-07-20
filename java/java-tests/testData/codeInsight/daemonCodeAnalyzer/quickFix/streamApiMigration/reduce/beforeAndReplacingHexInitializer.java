// "Replace with reduce()" "true"

import java.util.*;

public class Main {
  public void testBitwiseAndReplacingInitializer() {
    int[] arr = new int[]{1, 2, 3, 4};
    int acc = 0xFFFFFFFF;
    for <caret> (int i: arr) {
      acc &= i;
    }
  }
}