// "Replace lambda with method reference" "false"
import java.util.HashMap;
import java.util.Map;

class Test {
  public static void main(Map<Integer, Map<Object, Object>> map) {
      map.computeIfAbsent(123, key -> new Hash<caret>Map<>());
  }
}