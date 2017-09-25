// "Replace condition with Objects.requireNonNullElseGet" "true"

import java.util.*;

class Test {
  public void test(Object o) {
      return Objects.requireNonNullElseGet(o, Object::new);
  }
}