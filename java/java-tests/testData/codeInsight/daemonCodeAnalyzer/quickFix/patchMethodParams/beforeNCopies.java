// "Wrap 2nd argument using 'String.valueOf()'" "true-preview"
import java.util.*;

class Demo {
  void test() {
    List<String> stringList = Collections.<caret>nCopies(10, 20);
  }
}
