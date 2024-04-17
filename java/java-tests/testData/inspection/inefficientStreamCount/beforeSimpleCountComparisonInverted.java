// "Replace with 'stream.findAny().isPresent()'" "true-preview"

import java.util.stream.Stream;

class Test {
  boolean isPresent(Stream<String> stream) {
    return 0 < stream.c<caret>ount();
  }
}