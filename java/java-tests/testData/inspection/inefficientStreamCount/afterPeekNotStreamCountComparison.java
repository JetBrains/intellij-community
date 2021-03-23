// "Replace with 'stream.findAny().isPresent()'" "true"

import java.util.stream.Collectors;
import java.util.stream.Stream;

class Test {
  boolean isEmpty(Stream<String> stream) {
    return stream.peek(System.out::println).collect(Collectors.toList()).stream().limit(42).findAny().isPresent();
  }
}