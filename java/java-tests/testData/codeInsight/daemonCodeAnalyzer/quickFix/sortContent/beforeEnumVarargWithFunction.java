// "Sort content" "false"

import java.util.*;

public class Main {
  enum E {A,B,C,D}

  private static void foo(String a, E... vararg) {}

  private E enumConstant() {}

  private void test() {
    foo("bar", E.B, E.A,<caret> enumConstant(), E.D, E.A);
  }
}
