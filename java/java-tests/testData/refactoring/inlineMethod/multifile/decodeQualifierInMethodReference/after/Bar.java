import java.util.Map;
import java.util.Set;

public class Bar {
  void bar() {
      Function<Map.Entry<Long, Set<Integer>>, Long> getKey = Map.Entry::getKey;

  }
}