// "Replace with 'stream.anyMatch()'" "true"

import java.util.Arrays;

class Test {
  boolean anyMatch() {
    return 0 < Arrays.asList("ds", "e", "fe").stream().filter(s -> s.length() > 1).c<caret>ount();
  }
}