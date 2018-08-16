// "Wrap 'integers' with 'HashSet'" "true"
import java.util.*;

class Test {
  void testComparator() {
    var integers = Set.of(4, 3, 2, 1);
    integers.a<caret>dd(123);
  }
}