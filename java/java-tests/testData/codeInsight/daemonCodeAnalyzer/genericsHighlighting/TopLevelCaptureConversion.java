import java.util.Collection;
import java.util.Collections;
import java.util.Map;

class Test {
  private void foo(Map<String, ? extends Collection<?>> map, String severity) {
    firstNonNull(map.get(severity), Collections.emptyList());
  }
  private static <T> T firstNonNull(T first, T second) {
    return first;
  }
}
