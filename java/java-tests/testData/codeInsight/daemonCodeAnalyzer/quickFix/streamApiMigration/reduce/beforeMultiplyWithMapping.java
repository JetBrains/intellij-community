// "Replace with reduce()" "true"

import java.util.*;

public class Main {
  public void testMultiplyWithMapping() {
    int[] arr = new int[]{1, 2, 3, 4};
    int acc = 1;
    for <caret> (int i : arr) {
      acc *= i + 2;
    }
  }
}