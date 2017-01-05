// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.Objects;

public class Main {
  String test(String[] strings) {
    return Arrays.stream(strings).filter(Objects::nonNull).an<caret>yMatch(s -> !s.startsWith("xyz")) ? "s" : null;
  }
}
