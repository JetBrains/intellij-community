// "Replace 'stream.count() > 0' with 'stream.findAny().isPresent()'" "true"

import java.util.stream.Stream;

class Test {
  boolean isPresent(Stream<String> stream) {
    return 0 < stream.c<caret>ount();
  }
}