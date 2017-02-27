// "Replace with 'Map.of' call" "false"
import java.util.*;

public class Test {
  public void test() {
    Map<String, String> myMap = new HashMap<>();
    myMap.put("a", "1");
    myMap.put("b", "1");
    myMap.put("c", "1");
    myMap.put("d", "1");
    myMap.put("e", "1");
    myMap.put("f", "1");
    myMap.put("g", "1");
    myMap.put("h", "1");
    myMap.put("i", "1");
    myMap.put("j", "1");
    myMap.put("k", "1");
    myMap = Collections.unmodifia<caret>bleMap(myMap);
  }
}