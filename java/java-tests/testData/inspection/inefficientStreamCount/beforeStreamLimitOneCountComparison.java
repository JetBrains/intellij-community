// "Replace 'stream.count() > 0' with 'stream.findAny().isPresent()'" "false"

import java.util.stream.Stream;

class Test {
  boolean isPresent(Stream<String> stream) {
    return stream.limit(1).c<caret>ount() > 0;
  }
}