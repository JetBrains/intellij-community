// "Replace 'stream().forEach()' with 'forEach()' (may change semantics)" "true-preview"

import java.util.Arrays;
import java.util.Collection;

class Test {
  void print() {
    Collection<Character> def = Arrays.asList('d', 'e', 'f');
    def.forEach(System.out::print);
  }
}