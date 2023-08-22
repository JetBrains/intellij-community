// "Replace with 'stream.anyMatch()'" "true"

import java.util.Arrays;

class Test {
  boolean anyMatch() {
    return Arrays.asList("ds", "e", "fe")./*a*/stream(/*b*/)/*c*/.filter(s -> s.length() > 1).c<caret>ount() > /*d*/0;
  }
}