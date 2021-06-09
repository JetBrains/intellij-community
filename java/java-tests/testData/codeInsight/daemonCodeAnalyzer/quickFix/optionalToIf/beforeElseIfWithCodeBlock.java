// "Fix all ''Optional' can be replaced with sequence of 'if' statements' problems in file" "true"

import java.util.*;

class Test {

  void statementWithIfPresentWithMultipleInstuctionsGeneratesCodeBlock(String s) {
    Optional.<caret>ofNullable(s)
      .ifPresent(s1 -> {
        s1 = s1.trim();
        System.out.println(s1);
      });
  }
}