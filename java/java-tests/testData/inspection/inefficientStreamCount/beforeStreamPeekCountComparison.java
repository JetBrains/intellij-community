// "Replace 'stream.count() > 0' with 'stream.findAny().isPresent()'" "false"

import java.util.stream.Stream;

class Test {
  boolean isPresent(Stream<String> stream) {
    return stream.peek(System.out::println).c<caret>ount() > 0;
  }
}