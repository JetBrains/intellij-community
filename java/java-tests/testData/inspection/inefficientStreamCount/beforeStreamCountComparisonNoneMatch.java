// "Replace with 'stream.noneMatch()'" "true"

import java.util.Arrays;

class Test {
  boolean noneMatch() {
    return Arrays.asList("ds", "e", "fe").stream().filter(s -> s.length() > 1).c<caret>ount() == 0;
  }
}