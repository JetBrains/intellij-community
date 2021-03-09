// "Replace 'stream.count() == 0' with '!stream.findAny().isPresent()'" "true"

import java.util.stream.Stream;

class Test {
  boolean notIsPresent(Stream<String> stream) {
    return !stream.findAny().isPresent();
  }
}