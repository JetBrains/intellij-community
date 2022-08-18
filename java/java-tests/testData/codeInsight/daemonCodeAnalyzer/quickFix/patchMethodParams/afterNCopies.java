// "Wrap 2nd argument using 'String.valueOf()'" "true-preview"
import java.util.*;

class Demo {
  void test() {
    List<String> stringList = Collections.nCopies(10, String.valueOf(20));
  }
}
