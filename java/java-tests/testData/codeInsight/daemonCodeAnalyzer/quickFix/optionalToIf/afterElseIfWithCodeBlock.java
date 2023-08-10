// "Fix all ''Optional' can be replaced with sequence of 'if' statements' problems in file" "true"

import java.util.*;

class Test {

  void statementWithIfPresentWithMultipleInstuctionsGeneratesCodeBlock(String s) {
      if (s != null) {
          s = s.trim();
          System.out.println(s);
      }
  }
}