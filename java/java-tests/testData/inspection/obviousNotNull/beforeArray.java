// "Remove redundant null-check" "true"

import java.util.Objects;

public class Test {
  void test() {
    Objects.requireNonNull(new int<caret>[foo()]);
  }

  native int foo();
}