// "Replace with 'Map.ofEntries' call" "true"
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class Test {
  static final Map<String, String> map;

  static {
      //_map.put("a", "b");
      map = Map.ofEntries(Map.entry("c", "d"), Map.entry("e", "f"), Map.entry("g", "h"), Map.entry("i", "j"), Map.entry("k", "l"),
              // and m
              Map.entry("m", "n"), Map.entry("o", "p"), Map.entry("r", "s"), Map.entry("t", /*uuuu*/"u"), Map.entry("v", "w"), Map.entry("x", /*you*/ "y"),
              // q was forgotten!
              Map.entry("q", "z"));
  }
}
