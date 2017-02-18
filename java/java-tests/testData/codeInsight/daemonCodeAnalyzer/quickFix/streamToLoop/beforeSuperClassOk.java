// "Replace Stream API chain with loop" "true"

import java.util.stream.Stream;
import java.util.Objects;

public class Main {
  Main(boolean b) {}

  void test(boolean b) {}

  static class Child extends Main {
    // cannot replace as replacement would generate a statement before "super" call
    Child() {
      super(false);
      super.test(Stream.of("a", "b", "c").an<caret>yMatch(Objects::nonNull));
    }
  }
}