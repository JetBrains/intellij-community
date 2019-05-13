// "Replace with 'Map.of' call" "true"
import java.util.*;

public class Test {
  public void test() {
    Map<String, String> myMap = new HashMap<>();
    myMap.put("a", "b");
    myMap.put("c", "b");
    myMap = Collections.unmo<caret>difiableMap(myMap);
  }
}