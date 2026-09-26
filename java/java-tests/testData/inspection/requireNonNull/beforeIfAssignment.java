// "Replace 'if' statement with 'Objects.requireNonNullElseGet()' call" "true"

import java.util.*;

class Util {
  String method(String s) {return s;}
}

class Test {
  public void test(Object o) {
    Object another;
    if<caret>(o == null) {
      another = new Object();
    } else {
      another = o;
    }
  }
}