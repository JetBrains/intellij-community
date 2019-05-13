// "Replace with 'putIfAbsent' method call" "true"
import java.util.Map;

class Test{

  Integer m2(Map<String, Integer> map) {
    if (map.ge<caret>t("asd") == null) {
      return map.put("asd", 123);
    } else {
      return map.get("asd");
    }
  }

}