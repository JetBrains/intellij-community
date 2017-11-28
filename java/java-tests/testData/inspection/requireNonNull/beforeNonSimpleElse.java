// "Replace condition with Objects.requireNonNullElseGet" "true"

import java.util.*;

class Test {
  public void test(Object o) {
    if<caret>(o == null) {
      return new Object();
    } else {
      return o;
    }
  }
}