// "Use 'orElseGet' method with functional argument" "true"

import java.util.Optional;

class Test {
  String createDefaultString(int x) {
    return "foo"+x;
  }

  public void test(Optional<String> opt) {
    int x = 5;
    String result = opt.orElse(createDef<caret>aultString(x));
  }
}