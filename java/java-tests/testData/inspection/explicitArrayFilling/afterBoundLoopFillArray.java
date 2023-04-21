// "Replace loop with 'Arrays.setAll()' method call" "true"

import java.util.Arrays;

class Test {

  public static Object[] init(int n, boolean b) {
    Object[] data = new Object[n];
      Arrays.setAll(data, j -> (j / 2 + n == 0) ? "1" : new Object());
    return data;
  }
}