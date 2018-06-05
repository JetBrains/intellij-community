// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.stream.Stream;
import java.util.Objects;

public class Main {
  Main(boolean b) {}

  void test(boolean b) {}

  static class Child extends Main {
    // cannot replace as replacement would generate a statement before "super" call
    Child() {
      super(false);
        boolean b = false;
        for (String s: Arrays.asList("a", "b", "c")) {
            if (s != null) {
                b = true;
                break;
            }
        }
        super.test(b);
    }
  }
}