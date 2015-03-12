import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.LongAdder;

class Test {

  {
    Set<Map.Entry<String, Integer>> sort = new TreeSet<>((x, y) -> Integer.compare(x.getValue(), y.getValue()));
    Set<Map.Entry<String, LongAdder>> sort2 = new TreeSet<>((x, y) -> Long.compare(x.getValue().longValue(), y.getValue().longValue()));

    Set<Map.Entry<String, Integer>> sort3 = new TreeSet<>((x, y) -> {
      return Integer.compare(x.getValue(), y.getValue());
    });

    Set<Map.Entry<String, LongAdder>> sort4 = new TreeSet<>((x, y) -> {
      return Long.compare(x.getValue().longValue(), y.getValue().longValue());
    });
  }
}