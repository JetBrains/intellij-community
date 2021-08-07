// "Replace with 'stream.findAny().isEmpty()'" "false"

import java.util.stream.Collectors;
import java.util.stream.Stream;

class Test {
  boolean noneMatch(Stream<String> stream) {
    return 0 == stream.peek(System.out::println).filter(s -> s.length() > 1).c<caret>ount();
  }
}