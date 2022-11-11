// "Replace with 'String.join'" "true-preview"

import java.util.stream.*;

class Test {
  void test(CharSequence[] data) {
    String result = String.join(", ", data);
  }
}