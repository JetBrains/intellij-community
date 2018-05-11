// "Replace with 'String.join'" "false"

import java.util.stream.*;

class Test {
  void test(CharSequence[] data) {
    String result = Stream.<CharSequence[]>of(data).coll<caret>ect(Collectors.joining(", "));
  }
}