// "Replace condition with Objects.requireNonNullElse" "true"

import java.util.*;

class Test {
  public void test(String o) {
      return Objects.requireNonNullElse(o, "");
  }
}