// "Replace with 'stream.findAny().isPresent()'" "true-preview"

import java.util.stream.Stream;

class Test {
  boolean isPresent(Stream<String> stream) {
    return stream.skip(42).c<caret>ount() > 0;
  }
}