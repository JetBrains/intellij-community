// "Replace condition with Objects.requireNonNullElse" "true"

import java.util.*;

class Util {
  String method(String s) {return s;}
}

class Test {
  public void test(String o) {
    Object another;
    if<caret>(o == null) {
      Util.method("Hello");
    } else {
      Util.method(o);
    }
  }
}