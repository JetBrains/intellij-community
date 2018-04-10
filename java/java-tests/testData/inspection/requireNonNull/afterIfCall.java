// "Replace condition with Objects.requireNonNullElse" "GENERIC_ERROR_OR_WARNING"

import java.util.*;

class Util {
  String method(String s) {return s;}
}

class Test {
  public void test(String o) {
    Object another;
      Util.method(Objects.requireNonNullElse(o, "Hello"));
  }
}