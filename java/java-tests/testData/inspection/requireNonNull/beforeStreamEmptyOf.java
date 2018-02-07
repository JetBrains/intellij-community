// "Replace condition with Stream.ofNullable" "true"

import java.util.*;
import java.util.stream.Stream;

class Util {
  String method(String s) {return s;}
}

class Test {
  public Stream test(Object val) {
    if<caret> (val == null) {
      return Stream.empty();
    } else {
      return Stream.ofNullable(val);
    }
  }
}