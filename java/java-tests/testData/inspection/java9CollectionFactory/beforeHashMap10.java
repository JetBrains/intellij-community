// "Replace with 'Map.of' call" "true"
import java.util.*;

public class Test {
  public void test() {
    Map<String, String> myMap = new HashMap<>();
    myMap.put("a", "1");
    myMap.put("b", "1");
    myMap.put("c", "1");

    // D follows
    myMap.put("d", "1");
    myMap.put("e", /* this is also 1*/ "1");
    myMap.put("f", "1");
    myMap.put("g", "1"); // G is important!
    myMap.put("h", "1");
    myMap.put("i", "1");

    /* Finally J */
    myMap./* why not putting comment inside the call expression? */put("j", "1");
    myMap = Collections.unmodifia<caret>bleMap(myMap);
  }
}