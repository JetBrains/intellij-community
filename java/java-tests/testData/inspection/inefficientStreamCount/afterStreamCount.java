// "Replace Collection.stream().count() with Collection.size()" "true"

import java.util.Arrays;

class Test {
  long cnt() {
      /*count*/
      return (long) Arrays.asList('d', 'e', 'f')./*stream*/size()/*after*/;
  }
}