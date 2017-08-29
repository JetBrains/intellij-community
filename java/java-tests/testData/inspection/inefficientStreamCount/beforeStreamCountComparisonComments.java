// "Replace Stream().filter().count() > 0 with stream.anyMatch()" "true"

import java.util.Arrays;

class Test {
  long cnt() {
    return Arrays.asList('ds', 'e', 'fe')./*a*/stream(/*b*/)/*c*/.filter(s -> s.length() > 1).c<caret>ount() > /*d*/0;
  }
}