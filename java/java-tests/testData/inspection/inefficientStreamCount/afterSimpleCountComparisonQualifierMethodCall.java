// "Replace 'stream.count() > 0' with 'stream.findAny().isPresent()'" "true"

import java.util.stream.Stream;

class Test {
  boolean isPresent(Stream<String> stream) {
    return stream.skip(42).findAny().isPresent();
  }
}