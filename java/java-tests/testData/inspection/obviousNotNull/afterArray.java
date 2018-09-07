// "Remove redundant null-check" "true"

import java.util.Objects;

public class Test {
  void test() {
      foo();
  }

  native int foo();
}