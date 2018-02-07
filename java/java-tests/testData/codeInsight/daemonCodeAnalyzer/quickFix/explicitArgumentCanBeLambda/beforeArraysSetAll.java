// "Use 'setAll' method with functional argument" "false"

import java.util.Arrays;

class Test {
  int computeValue() {
    return 0;
  }

  public void test(int[] arr) {
    // Should not suggest setAll replacement as lambda might be called many times
    Arrays.fill(arr, co<caret>mputeValue());
  }
}