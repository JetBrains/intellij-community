// "Sort content" "false"

import java.util.*;

public class Main {
  private void test() {
    new String[] {"bbb", <caret>"aaa", "ccc", null};
  }
}
