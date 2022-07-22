// "Replace conditional expression with 'Objects.requireNonNullElse()' call" "true"

import java.util.*;

class X {
  String y;

  static String test(X x) {
    return x.y == null ? "foo"<caret> : x.y;
  }
}