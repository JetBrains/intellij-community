// "Replace with 'Map.ofEntries' call" "true"
import java.util.*;

public class Test {
  public void test() {
    Map<String, String> myMap;
      myMap = Map.ofEntries(Map.entry("a", "1"), Map.entry("b", "1"), Map.entry("c", "1"), Map.entry("d", "1"), Map.entry("e", "1"), Map.entry("f", "1"), Map.entry("g", "1"), Map.entry("h", "1"), Map.entry("i", "1"), Map.entry("j", "1"), Map.entry("k", "1"));
  }
}