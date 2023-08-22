// "Use 'requireNonNullElse' method without lambda" "true-preview"

import java.util.*;

class Test {
  public String test(String foo, String bar) {
    return Objects.requireNonNullElseGet(foo, () <caret>-> bar);
  }
}