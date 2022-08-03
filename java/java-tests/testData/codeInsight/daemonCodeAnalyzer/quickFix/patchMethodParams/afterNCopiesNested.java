// "Wrap argument using 'String.valueOf()'" "true-preview"
import java.util.*;

class Demo {
  void test() {
    List<Set<String>> nCopiesNested = Collections.nCopies(10, Set.of(String.valueOf(20)));
  }
}
