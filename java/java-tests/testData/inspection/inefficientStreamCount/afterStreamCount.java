// "Replace with 'Collection.size()'" "true-preview"

import java.util.Arrays;

class Test {
  long cnt() {
      /*count*/
      return Arrays.asList('d', 'e', 'f')./*stream*/size()/*after*/;
  }
}