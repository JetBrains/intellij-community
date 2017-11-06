// "Sort content" "true"

import java.util.*;

public class Main {
  private static void foo(String a, int... vararg) {}

  private void test() {
    foo("bar", 1, 3, 4);
  }
}
