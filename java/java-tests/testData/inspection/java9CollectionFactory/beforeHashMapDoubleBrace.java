// "Replace with 'Map.of' call" "true"
import java.util.*;

public class Test {
  private static final Map<Integer, String> myMap2 = Collections.unmodifiab<caret>leMap(new HashMap<>() {
    {
      put(1, "one");
      put(2, "two");
    }
  });
}