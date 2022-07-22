// "Replace conditional expression with 'Objects.requireNonNullElse()' call" "true"

import java.util.*;

class X {
  String y;

  static String test(X x) {
    return Objects.requireNonNullElse(x.y, "foo");
  }
}