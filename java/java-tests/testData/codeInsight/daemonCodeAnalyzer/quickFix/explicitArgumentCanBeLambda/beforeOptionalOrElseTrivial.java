// "Use 'orElseGet' method with functional argument" "false"

import java.util.Optional;

class Test {
  public void test(Optional<String> opt) {
    String result = opt.orElse("f<caret>oo");
  }
}