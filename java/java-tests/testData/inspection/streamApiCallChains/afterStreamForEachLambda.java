// "Replace 'stream().forEach()' with 'forEach()' (may change semantics)" "true-preview"

import java.util.Arrays;

class Test {
  void print() {
    Arrays.asList('d', 'e', 'f').forEach(c -> System.out.print(" " + c));
  }
}