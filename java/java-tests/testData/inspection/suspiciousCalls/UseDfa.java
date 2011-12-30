import java.lang.Integer;
import java.util.Map;

class Clazz {
  void f(Map<Integer, String> map, Object o) {
    if (o instanceof Integer && map.containsKey(o)) {
      System.out.println();
    }
  }

}