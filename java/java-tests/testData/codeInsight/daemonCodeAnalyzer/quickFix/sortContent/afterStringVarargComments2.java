// "Sort content" "true-preview"

import java.util.*;

public class Main {
  private static void foo(String... vararg) {}

  private void test() {
    foo(
            "aaa",
            "bbb",

            "ccc" //comment
    );
  }
}
