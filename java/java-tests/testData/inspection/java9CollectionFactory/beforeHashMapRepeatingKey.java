// "Replace with 'Map.of' call" "false"
import java.util.*;

public class Test {
  public void test() {
    Map<String, String> myMap = new HashMap<>();
    myMap.put("a", "b");
    myMap.put("a", "c");
    myMap = Collections.unmo<caret>difiableMap(myMap);
  }
}