// "Sort content" "true-preview"

import java.util.*;

public class Main {
  private static void foo(int a, int... vararg) {}

  private void test() {
    foo(5, 1, 3, 4);
  }
}
