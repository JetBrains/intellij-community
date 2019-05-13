// "Replace condition with Objects.requireNonNullElse" "true"

import java.util.*;

class X {
  String y;

  static String test(X x) {
    return x.y == null ? "foo"<caret> : x.y;
  }
}