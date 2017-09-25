// "Replace condition with Objects.requireNonNullElse" "false"

import java.util.*;

class Test {
  void work(Object o) {}

  private static Object nullable() {
    return null;
  }

  public void test(Object o) {
    work(o == null? "" <caret>: nullable());
  }
}