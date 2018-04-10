// "Sort content" "false"

import java.util.*;

public class Main {
  void foo(int... vararg) {
  }

  void test() {
    foo(1, 4, 3,<caret> (int) 1);
  }
}
