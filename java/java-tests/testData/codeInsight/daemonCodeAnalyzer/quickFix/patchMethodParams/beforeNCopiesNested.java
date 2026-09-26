// "Wrap argument using 'String.valueOf()'" "true-preview"
import java.util.*;

class Demo {
  void test() {
    List<Set<String>> nCopiesNested = Collections.<caret>nCopies(10, Set.of(20));
  }
}
