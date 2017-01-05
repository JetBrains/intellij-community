// "Replace Collection.stream().count() with Collection.size()" "true"

import java.util.Arrays;

class Test {
  int cnt() {
    return (int) Arrays.asList('d', 'e', 'f').stream(/*inside*/).c<caret>ount();
  }
}