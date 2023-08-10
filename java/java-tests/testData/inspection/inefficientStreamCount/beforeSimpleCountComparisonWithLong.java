// "Replace with 'stream.findAny().isEmpty()'" "true"

import java.util.stream.Stream;

class Test {
  private final long ONE = 1;
  boolean isEmpty(Stream<String> stream) {
    return stream.c<caret>ount() < ONE;
  }
}