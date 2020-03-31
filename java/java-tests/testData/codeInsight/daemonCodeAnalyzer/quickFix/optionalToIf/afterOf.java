// "Fix all 'Optional can be replaced with sequence of if statements' problems in file" "true"

import java.util.*;

class Test {

  void exceptionIsThrownOnNullValue(String in) {
      if (in == null) throw new NullPointerException();
      String out = in;
  }

  void exceptionIsTheSameWithOrElseThrow(String in) {
      if (in == null) throw new NullPointerException();
      Integer len = getLen(in);
      if (len == null) throw new IllegalArgumentException("value is null");
      Integer out = len;
  }

  void redundantCheckIsRemoved() {
    String in = "not null value";
      String out = in;
  }

  private Integer getLen(String s) {
    return s.startsWith("abc") ? null : s;
  }

  private Integer filterLen(String s) {
    return s.length() > 42 ? s.length() : null;
  }
}