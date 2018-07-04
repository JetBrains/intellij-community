// "Sort content" "true"

import java.util.*;

public class Main {
  private static void foo(String a, String... vararg) {}

  private void test() {
    foo(/*6*/"bar", // 1
        // 4
        "foo"/*3*/, // 2
    // 5
    <caret>"bar",
      "baz"// 7
    // 8
    );
  }
}
