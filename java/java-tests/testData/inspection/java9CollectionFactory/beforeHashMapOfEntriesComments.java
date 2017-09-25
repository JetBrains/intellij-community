// "Replace with 'Map.ofEntries' call" "true"
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class Test {
  static final Map<String, String> map;

  static {
    Map<String, String> _map = new HashMap<>();
    //_map.put("a", "b");
    _map.put("c", "d");
    _map.put("e", "f");
    _map.put("g", "h");
    _map.put("i", "j");
    _map.put("k", "l");
    // and m
    _map.put("m", "n");
    _map.put("o", "p");
    _map.put("r", "s");
    _map.put("t" /*uuuu*/, "u");
    _map.put("v", "w");
    _map.put("x", /*you*/ "y");
    // q was forgotten!
    _map.put("q", "z");
    map = Collections.unmodifiab<caret>leMap(_map);
  }
}
