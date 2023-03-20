// "Convert argument to 'long'" "true-preview"
import java.util.*;

class Demo {
  void test() {
    Optional<Long> opt = <caret>Optional.of(123);
  }
}
