// "Replace condition with Objects.requireNonNullElse" "true"

import java.util.*;

class Test {
  public void test(Object o) {
    if<caret>(o == null) {
      o = "";
    }
  }
}