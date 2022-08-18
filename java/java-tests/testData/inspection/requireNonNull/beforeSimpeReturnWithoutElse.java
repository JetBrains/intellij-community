// "Replace 'if' statement with 'Objects.requireNonNullElse()' call" "true"

import java.util.*;

class Test {
  public void test(String o) {
    if<caret>(o == null) {
      return "";
    }
    return o;
  }
}