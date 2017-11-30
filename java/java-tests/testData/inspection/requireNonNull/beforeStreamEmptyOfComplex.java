// "Replace condition with Stream.ofNullable" "true"

import java.util.*;
import java.util.stream.Stream;

class Util {
  Stream method(Stream s) {return s;}
}

class Test {
  public Stream test(Object val) {
    if<caret> (val == null) {
      return Util.method(Stream.empty());
    } else {
      return Util.method(Stream.of(val));
    }
  }
}