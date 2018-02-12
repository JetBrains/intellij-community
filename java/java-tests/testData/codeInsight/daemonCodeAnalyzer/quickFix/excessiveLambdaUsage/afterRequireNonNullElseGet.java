// "Use 'requireNonNullElse' method without lambda" "true"

import java.util.*;

class Test {
  public String test(String foo, String bar) {
    return Objects.requireNonNullElse(foo, bar);
  }
}