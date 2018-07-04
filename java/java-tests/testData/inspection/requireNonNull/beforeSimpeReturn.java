// "Replace condition with Objects.requireNonNullElse" "true"

import java.util.*;

class Util {
  String method(String s) {return s;}
}

class Test {
  public void test(String o) {
    if<caret>(o == null) {
      return Util.method("Adasdasdas".trim()).trim();
    } else {
      return Util.method(o.trim()).trim();
    }
  }
}