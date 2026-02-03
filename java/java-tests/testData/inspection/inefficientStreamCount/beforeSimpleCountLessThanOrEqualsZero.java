// "Replace with 'stream.findAny().isEmpty()'" "true-preview"

import java.util.stream.Stream;

class Test {
  boolean isEmpty(Stream<String> stream) {
    return 0 >= stream.c<caret>ount();
  }
}