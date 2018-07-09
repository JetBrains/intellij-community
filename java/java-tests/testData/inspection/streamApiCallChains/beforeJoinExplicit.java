// "Replace with 'String.join'" "true"

import java.util.stream.*;

class Test {
  void test() {
    String result = Stream.of("a"/*a*/, /*b*/"b", "c"/*c*/)./*d*/coll<caret>ect(/*e*/Collectors.joining());
  }
}