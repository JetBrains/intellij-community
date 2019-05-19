// "Remove erroneous '!= null'" "true"

import java.util.Objects;

public class Test {
  void test(String foo) {
    Objects.requireNonNull(foo);
  }

  native int foo();
}