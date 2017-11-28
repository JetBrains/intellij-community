// "Replace Collection.stream().count() with Collection.size()" "true"

import java.util.Arrays;

class Test {
  int cnt() {
      /*inside*/
      return Arrays.asList('d', 'e', 'f').size();
  }
}