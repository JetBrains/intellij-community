// "Replace with 'Collection.size()'" "true-preview"

import java.util.Arrays;

class Test {
  void cnt() {
    Arrays.asList('d', 'e', 'f').stream().c<caret>ount();
  }
}