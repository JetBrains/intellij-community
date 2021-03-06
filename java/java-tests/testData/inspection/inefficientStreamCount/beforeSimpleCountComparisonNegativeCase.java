// "Replace 'stream.count() > 0' with 'stream.findAny().isPresent()'" "false"

import java.util.Arrays;

class Test {
  long cnt() {
    return Arrays.asList('d', 'e', 'f')./*stream*/stream()./*count*/c<caret>ount()/*after*/ > 0;
  }
}