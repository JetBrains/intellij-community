// "Replace loop with 'Arrays.setAll()' method call" "true"

import java.util.Arrays;

class Test {

  void test(boolean choice) {
    Object[] arr = new Object[10];
      Arrays.setAll(arr, i -> (choice ? "foo" : new Object()));
  }

}