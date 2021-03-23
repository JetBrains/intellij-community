// "Replace with 'stream.anyMatch()'" "true"

import java.util.Arrays;

class Test {
  boolean anyMatch() {
      /*c*/
      /*d*/
      return Arrays.asList("ds", "e", "fe")./*a*/stream(/*b*/).anyMatch(s -> s.length() > 1);
  }
}