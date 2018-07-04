// "Replace Stream().filter().count() > 0 with stream.anyMatch()" "true"

import java.util.Arrays;

class Test {
  long cnt() {
      /*c*/
      /*d*/
      return Arrays.asList("ds", "e", "fe")./*a*/stream(/*b*/).anyMatch(s -> s.length() > 1);
  }
}