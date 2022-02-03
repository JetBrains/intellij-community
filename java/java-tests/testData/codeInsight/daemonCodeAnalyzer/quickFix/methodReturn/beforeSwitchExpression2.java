// "Make 'test' return 'java.lang.String'" "false"

import java.util.stream.Stream;

class Test {
  int test(int val) {
    return Stream.of("a", "b", "c").mapToInt(s -> {
      return switch (val) {
        default -> "bar"<caret>;
      };
    }).sum();
  }
}