// "Replace with 'Collection.size()'" "true"

import java.util.Arrays;

class Test {
  long cnt() {
    return Arrays.asList('d', 'e', 'f')./*stream*/stream()./*count*/c<caret>ount()/*after*/;
  }
}