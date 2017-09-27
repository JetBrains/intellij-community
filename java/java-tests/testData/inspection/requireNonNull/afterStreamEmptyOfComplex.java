// "Replace condition with Stream.of" "true"

import java.util.*;
import java.util.stream.Stream;

class Util {
  Stream method(Stream s) {return s;}
}

class Test {
  public Stream test(Object val) {
      return Util.method(Stream.ofNullable(val));
  }
}