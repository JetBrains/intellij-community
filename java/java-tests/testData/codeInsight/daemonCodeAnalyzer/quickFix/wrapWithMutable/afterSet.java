// "Wrap 'integers' with 'HashSet'" "true"
import java.util.*;

class Test {
  void testComparator() {
    var integers = new HashSet<>(Set.of(4, 3, 2, 1));
    integers.add(123);
  }
}