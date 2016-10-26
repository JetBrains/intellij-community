// "Replace Collection.stream().count() with Collection.size()" "true"

import java.util.Arrays;

class Test {
  short cnt() {
    return (short) Arrays.asList('d', 'e', 'f').stream().c<caret>ount();
  }
}