// "Replace with 'Collection.size()'" "true"

import java.util.Arrays;

class Test {
  void cnt() {
    Arrays.asList('d', 'e', 'f').stream().c<caret>ount();
  }
}