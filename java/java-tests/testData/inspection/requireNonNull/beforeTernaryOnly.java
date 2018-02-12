// "Replace condition with Objects.requireNonNullElse" "true"

import java.util.*;

class Test {
  void work(Object o) {

  }

  public void test(Object o) {
    work(o <caret>== null? "" : o);
  }
}