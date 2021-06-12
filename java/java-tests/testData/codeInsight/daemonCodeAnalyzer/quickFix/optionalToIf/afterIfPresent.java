// "Fix all ''Optional' can be replaced with sequence of 'if' statements' problems in file" "true"

import java.util.*;

class Test {

  void simple(String in) {
      if (in != null) System.out.println(in);
  }

  void lambdaIsNotSimplified(String in, String p1, String p2) {
    if (in == null || p1 == null) throw new IllegalArgumentException();
      /* comment1 */
      // comment2
      String tmp = "foo";
      tmp = "bar"; // comment2
  }

}