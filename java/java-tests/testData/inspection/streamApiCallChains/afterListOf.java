// "Replace List.of().stream() with Stream.of()" "true-preview"

import java.util.List;
import java.util.stream.Stream;

class Test {
  void test() {
    Stream.of(1, 2, 3, 42).forEach(System.out::println);
  }
}
