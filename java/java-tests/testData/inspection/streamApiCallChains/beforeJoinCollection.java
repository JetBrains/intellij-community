// "Replace with 'String.join'" "true-preview"

import java.util.*;
import java.util.stream.*;

class Test {
  void test(List<? extends CharSequence> list) {
    String result = list.stream().coll<caret>ect(Collectors.joining("+"));
  }
}