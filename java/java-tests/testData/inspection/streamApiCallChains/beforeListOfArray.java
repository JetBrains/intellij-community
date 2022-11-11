// "Replace List.of().stream() with Stream.of()" "true-preview"

import java.util.List;

class Test {
  void test() {
    List.of(new int[]{1, 2, 3, 42}).st<caret>ream().forEach(System.out::println);
  }
}
