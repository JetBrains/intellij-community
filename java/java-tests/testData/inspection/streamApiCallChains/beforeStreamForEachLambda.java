// "Replace 'stream().forEach()' with 'forEach()' (may change semantics)" "true"

import java.util.Arrays;

class Test {
  void print() {
    Arrays.asList('d', 'e', 'f').stream().fo<caret>rEach(c -> System.out.print(" " + c));
  }
}