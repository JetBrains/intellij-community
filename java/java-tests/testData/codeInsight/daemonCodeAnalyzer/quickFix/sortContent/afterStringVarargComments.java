// "Sort content" "true-preview"

import java.util.*;

public class Main {
  private static void foo(String a, String... vararg) {}

  private void test() {
    foo(/*6*/"bar", // 1
            // 4
            "bar",

            "baz" // 7
            // 8
            ,
            "foo" /*3*/ // 2
            // 5

    );
  }
}
