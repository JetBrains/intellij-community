// "Replace with reduce()" "true"

import java.util.*;

public class Main {
  public void testBitwiseAndHighestBit() {
    int[] arr = {0x80000000, 0xffffffff};
    int acc = -1;
    for <caret> (int i: arr) {
      acc &= i;
    }
  }
}