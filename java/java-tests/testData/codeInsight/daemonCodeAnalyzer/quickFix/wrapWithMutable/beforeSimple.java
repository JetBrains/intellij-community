// "Wrap 'integers' with 'ArrayList'" "true"
import java.util.*;

class Test {
  void testComparator() {
    List<Integer> integers = List.of(4, 3, 2, 1);
    integers.so<caret>rt(Comparator.naturalOrder());
  }
}