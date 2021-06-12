// "Fix all ''Optional' can be replaced with sequence of 'if' statements' problems in file" "true"

import java.util.*;

class Test {

  void exceptionIsThrownOnNullValue(String in) {
    String out = Optional.of<caret>(in).get();
  }

  void exceptionIsTheSameWithOrElseThrow(String in) {
    Integer out = Optional.of(in).map(s -> getLen(s)).orElseThrow(() -> new IllegalArgumentException("value is null"));
  }

  void redundantCheckIsRemoved() {
    String in = "not null value";
    String out = Optional.of(in).orElseThrow(() -> new IllegalArgumentException("value is null"));
  }

  private Integer getLen(String s) {
    return s.startsWith("abc") ? null : s;
  }

  private Integer filterLen(String s) {
    return s.length() > 42 ? s.length() : null;
  }
}