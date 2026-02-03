// "Replace with 'stream.findAny().isEmpty()'" "true-preview"

import java.util.stream.Stream;

class Test {
  boolean isEmpty(Stream<String> stream) {
    return stream.limit(100_000_000).findAny().isEmpty();
  }
}