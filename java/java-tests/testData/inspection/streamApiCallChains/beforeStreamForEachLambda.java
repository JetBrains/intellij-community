// "Replace Collection.stream().forEach() with Collection.forEach()" "true"

import java.util.Arrays;
import java.util.Collection;

class Test {
  void print() {
    Collection<Character> def = Arrays.asList('d', 'e', 'f');
    def.st<caret>ream().forEach(c -> System.out.print(" " + c));
  }
}