// "Replace Stream().filter().count() > 0 with stream.anyMatch()" "true"

import java.util.Arrays;

class Test {
  long cnt() {
    return Arrays.asList("ds", "e", "fe").stream().anyMatch(s -> s.length() > 1);
  }
}