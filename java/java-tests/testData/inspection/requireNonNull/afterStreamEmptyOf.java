// "Replace 'if' statement with 'Stream.ofNullable()' call" "true"

import java.util.*;
import java.util.stream.Stream;

class Util {
  String method(String s) {return s;}
}

class Test {
  public Stream test(Object val) {
      return Stream.ofNullable(val);
  }
}