
import java.util.Collection;
import java.util.Map;

class Test {
  private void foo(Map.Entry<Integer, ? extends Collection<String>> entry) {
    for (String propertyName : entry.getValue()) {}
  }
}
