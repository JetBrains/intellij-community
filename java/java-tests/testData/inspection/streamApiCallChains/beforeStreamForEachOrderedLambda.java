// "Replace 'stream().forEachOrdered()' with 'forEach()' (may change semantics)" "true-preview"

import java.util.Arrays;

class Test {
  void print() {
    Arrays.asList('d', 'e', 'f').stream().forEac<caret>hOrdered(c -> System.out.print(" " + c));
  }
}