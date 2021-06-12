// "Fix all ''Optional' can be replaced with sequence of 'if' statements' problems in file" "true"

import java.util.*;

class Test {

  void simple(String in) {
    Optional.ofNullable<caret>(in).ifPresent(System.out::println);
  }

  void lambdaIsNotSimplified(String in, String p1, String p2) {
    if (in == null || p1 == null) throw new IllegalArgumentException();
    Optional./* comment1 */ofNullable(in).ifPresent(s -> {
      String tmp = "foo";
      tmp = "bar"; // comment2
    });
  }

}