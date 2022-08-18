// "Replace conditional expression with 'Objects.requireNonNullElse()' call" "true"

import java.util.*;

class Test {
  public void test(Object o) {
    o = o <caret>== null? "" : o;
  }
}