// "Replace loop with 'Arrays.setAll()' method call" "true"

import java.util.Arrays;

class Test {

  void fill2DArray() {
    final double[][] arr = new double[2][];
      Arrays.setAll(arr, i -> new double[1]);
  }
}