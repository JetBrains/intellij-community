// "Replace 'stream.count() > 0' with 'stream.findAny().isPresent()'" "false"

import java.util.Arrays;

class Test {
  boolean isPresent() {
    return Arrays.asList('d', 'e', 'f').stream().c<caret>ount() > 0;
  }
}