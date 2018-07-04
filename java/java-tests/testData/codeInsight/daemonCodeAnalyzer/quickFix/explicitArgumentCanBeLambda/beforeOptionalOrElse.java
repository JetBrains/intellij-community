// "Use 'orElseGet' method with functional argument" "true"

import java.util.Optional;

class Test {
  String createDefaultString() {
    return "foo";
  }

  public void test(Optional<String> opt) {
    String result = opt.orElse(createDef<caret>aultString());
  }
}