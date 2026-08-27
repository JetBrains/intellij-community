import org.jspecify.annotations.NullMarked;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@NullMarked
class Test {
  public ConcurrentMap<String, String> map = new ConcurrentHashMap<>();

  int test() {
    String old = map.putIfAbsent("a", "b");
    if (old != null) {
      return 1;
    }
    return 0;
  }
}