// "Replace 'stream.count() == 0' with 'stream.findAny().isEmpty()'" "true"

import java.util.stream.Stream;

class Test {
  boolean isEmpty(Stream<String> stream) {
    return stream.c<caret>ount() == 0;
  }
}