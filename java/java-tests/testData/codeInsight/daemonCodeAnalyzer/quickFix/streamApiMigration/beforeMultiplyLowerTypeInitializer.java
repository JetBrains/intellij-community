// "Replace with reduce()" "true"

import java.util.*;

public class Main {
  public void testMultiplyWithLowerTypeInitializer() {
    int[] arr = new int[]{1, 2, 3, 4};
    short acc = (byte) 12;
    for <caret> (int i : arr) {
      acc *= i;
    }
  }
}