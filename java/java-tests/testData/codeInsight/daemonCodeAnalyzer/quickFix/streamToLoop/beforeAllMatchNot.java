// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.Objects;

public class Main {
  boolean test(String[] strings) {
    return !Arrays.stream(strings).filter(Objects::nonNull).allM<caret>atch(s -> s.startsWith("xyz"));
  }
}
