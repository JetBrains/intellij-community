// "Replace Collection.stream().count() with Collection.size()" "true"

import java.util.Arrays;

class Test {
  Long cnt() {
    return Arrays.asList('d', 'e', 'f').st<caret>ream().count();
  }
}