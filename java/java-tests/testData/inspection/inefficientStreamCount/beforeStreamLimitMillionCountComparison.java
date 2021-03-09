// "Replace 'stream.count() == 0' with 'stream.findAny().isEmpty()'" "true"

import java.util.stream.Stream;

class Test {
  boolean isEmpty(Stream<String> stream) {
    return 0 == stream.limit(100_000_000).c<caret>ount();
  }
}