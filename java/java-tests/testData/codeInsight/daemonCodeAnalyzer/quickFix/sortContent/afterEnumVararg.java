// "Sort content" "true"

import java.util.*;

public class Main {
  enum E {A,B,C,D}

  private static void foo(String a, E... vararg) {}

  private void test() {
    foo("bar", E.A, E.A, E.B, E.C, E.D);
  }
}
