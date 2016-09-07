// "Replace Collection.stream().count() with Collection.size()" "true"

import java.util.Arrays;

class Test {
  long cnt() {
    return (long) Arrays.asList('d', 'e', 'f').size();
  }
}