// "Replace Set.of().stream() with Stream.of()" "false"

import java.util.Set;

class Test {
  void test() {
    Set.of(1, 1, 2, 3, 42).st<caret>ream().forEach(System.out::println);
  }
}
