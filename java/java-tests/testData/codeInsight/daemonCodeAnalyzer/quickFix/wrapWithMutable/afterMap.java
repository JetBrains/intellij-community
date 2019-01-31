// "Wrap 'map' with 'HashMap'" "true"
import java.util.*;

class Test {
  void testComparator() {
    Map<String, Integer> map = new HashMap<>(Math.random() > 0.5 ? Collections.emptyMap() : Collections.singletonMap("foo", 1));
    map.put(123, 456);
  }
}