// "Replace loop with 'Arrays.fill()' method call" "true"

import java.util.Arrays;

class Test {

  private void testLambdas() {
    Supplier[] arr = new Supplier[10];
      Arrays.fill(arr, () -> new int[10]);
  }

}