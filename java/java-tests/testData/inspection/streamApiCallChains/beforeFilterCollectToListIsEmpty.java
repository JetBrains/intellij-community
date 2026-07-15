// "Replace with 'noneMatch()'" "true-preview"

import java.util.stream.*;

import static java.util.stream.Collectors.*;

class Test {
  boolean test(Stream<String> stream) {
    return stream.filter(String::isEmpty).collect(toList()).isEm<caret>pty();
  }
}
