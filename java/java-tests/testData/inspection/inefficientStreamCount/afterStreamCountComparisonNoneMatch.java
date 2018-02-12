// "Replace Stream().filter().count() == 0 with stream.noneMatch()" "true"

import java.util.Arrays;

class Test {
  long cnt() {
    return Arrays.asList("ds", "e", "fe").stream().noneMatch(s -> s.length() > 1);
  }
}