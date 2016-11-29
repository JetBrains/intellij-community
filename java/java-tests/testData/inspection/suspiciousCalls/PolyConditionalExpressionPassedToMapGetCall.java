
import java.util.List;
import java.util.Map;

class Test {

  void m(Map<Class, String> map) {
    map.get(map.size() > 10 ? Map.class : List.class);
  }

}