// "Wrap 'map' with 'HashMap'" "true"
import java.util.*;

class Test {
  void testComparator() {
    Map<String, Integer> map = Math.random() > 0.5 ? Collections.emptyMap() : Collections.singletonMap("foo", 1);
    map.p<caret>ut(123, 456);
  }
}