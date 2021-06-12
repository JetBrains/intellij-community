// "Fix all ''Optional' can be replaced with sequence of 'if' statements' problems in file" "true"

import java.util.*;

class Test {
  String notNullValueCheckIsRemoved(String in) {
    if (in == null) return "foo";
      return in;
  }
}