// "Replace condition with Objects.requireNonNullElseGet" "true"

import java.util.*;

class Util {
  String method(String s) {return s;}
}

class Test {
  public void test(Object o) {
    Object another;
      another = Objects.requireNonNullElseGet(o, Object::new);
  }
}