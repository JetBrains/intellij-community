// "Replace with count()" "true"

import java.util.Arrays;

public class Main {
  public int testPrimitiveArray(int[] data) {
      int count = (int) Arrays.stream(data).mapToLong(val -> val * val).filter(square -> square > 100).count();
      return count;
  }
}