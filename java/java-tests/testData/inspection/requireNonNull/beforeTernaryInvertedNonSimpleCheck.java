// "Replace conditional expression with 'Objects.requireNonNullElseGet()' call" "true"

import java.util.*;

class Test {
  public void test(Object o) {
    o = (o) <caret>!= null? o : new Object();
  }
}