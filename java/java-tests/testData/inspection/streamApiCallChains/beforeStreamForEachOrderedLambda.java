// "Replace Collection.stream().forEachOrdered() with Collection.forEach() (may change semantics)" "true"

import java.util.Arrays;

class Test {
  void print() {
    Arrays.asList('d', 'e', 'f').str<caret>eam().forEachOrdered(c -> System.out.print(" " + c));
  }
}