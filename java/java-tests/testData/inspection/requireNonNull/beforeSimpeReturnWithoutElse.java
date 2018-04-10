// "Replace condition with Objects.requireNonNullElse" "true"

import java.util.*;

class Test {
  public void test(String o) {
    if<caret>(o == null) {
      return "";
    }
    return o;
  }
}