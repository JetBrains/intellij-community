// "Use 'orElseGet' method with functional argument" "true-preview"

import java.util.Optional;

class Test {
  String createDefaultString() {
    return "foo";
  }

  public void test(Optional<String> opt) {
    String result = opt.orElseGet(this::createDefaultString);
  }
}