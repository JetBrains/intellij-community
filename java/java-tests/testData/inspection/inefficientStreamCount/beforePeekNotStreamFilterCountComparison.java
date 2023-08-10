// "Replace with 'stream.anyMatch()'" "true"

import java.util.stream.Collectors;
import java.util.stream.Stream;

class Test {
  boolean anyMatch(Stream<String> stream) {
    return stream.peek(System.out::println).collect(Collectors.toList()).stream().filter(s -> s.length() > 1).c<caret>ount() > 0;
  }
}