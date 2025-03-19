// "Replace with 'stream.findAny().isEmpty()'" "true-preview"

import java.util.stream.Stream;

class Test {
  boolean isEmpty(Stream<String> stream) {
    return stream.findAny().isEmpty();
  }
}