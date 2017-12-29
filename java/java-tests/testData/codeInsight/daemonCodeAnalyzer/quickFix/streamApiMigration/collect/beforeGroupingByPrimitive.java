// "Replace with collect" "true"
import java.util.*;

public class Main {
  public void test() {
    String[] values = {"a", "b", "c"};
    Map<String, List<Object>> map = new HashMap<>();
    f<caret>or (int i = 0; i < values.length; i++) {
      map.computeIfAbsent("X" + i, k -> new ArrayList<>()).add(values[i]);
    }
  }
}
