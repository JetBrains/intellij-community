// "Wrap argument using 'String.valueOf()'" "true-preview"
import java.util.*;

class Demo {
  void test(int value) {
    Optional<String> optStr = Optional.of(String.valueOf(value));
  }
}
