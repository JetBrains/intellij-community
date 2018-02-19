// "Sort content" "true"

import java.util.*;

public class Main {
  private static void foo(int... vararg) {}

  private void test() {
    foo(1, 3, 4);
  }
}
