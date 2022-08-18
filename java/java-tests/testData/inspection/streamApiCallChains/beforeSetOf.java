// "Replace Set.of().stream() with Stream.of()" "true-preview"

import java.util.Set;

class Test {
  void test() {
    Set.of(1, 2, 3, 42).st<caret>ream().forEach(System.out::println);
  }
}
