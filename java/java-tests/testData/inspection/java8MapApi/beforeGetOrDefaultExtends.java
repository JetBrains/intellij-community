// "Replace with 'getOrDefault' method call" "false"
import java.util.Map;

public class Main {
  public CharSequence test(Map<String, ? extends CharSequence> map) {
    // cannot replace as map.getOrDefault("xyz", "none") will result in compilation error
    if(map.co<caret>ntainsKey("xyz")) {
      return map.get("xyz");
    } else {
      return "none";
    }
  }
}