// "Replace with 'putIfAbsent' method call" "true"
import java.util.Map;

class Test{

  Integer m2(Map<String, Integer> map) {
      return map.putIfAbsent("asd", 123);
  }

}