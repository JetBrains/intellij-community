import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.LongAdder;

class Test {

  {
    Set<Map.Entry<String, Integer>> sort3 = new TreeSet<>((x, y) -> {
      return Integer.compare(x.get<ref>Value(), y.getValue());
    });
  }
}