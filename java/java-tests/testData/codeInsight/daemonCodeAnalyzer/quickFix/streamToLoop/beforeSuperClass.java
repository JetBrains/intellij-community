// "Replace Stream API chain with loop" "false"

import java.util.stream.Stream;
import java.util.Objects;

public class Main {
  Main(boolean b) {}

  static class Child extends Main {
    // cannot replace as replacement would generate a statement before "super" call
    Child() {
      super(Stream.of("a", "b", "c").an<caret>yMatch(Objects::nonNull));
    }
  }
}