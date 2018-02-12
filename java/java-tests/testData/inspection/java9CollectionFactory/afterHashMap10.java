// "Replace with 'Map.of' call" "true"
import java.util.*;

public class Test {
  public void test() {
    Map<String, String> myMap;

      myMap = Map.of("a", "1", "b", "1", "c", "1",

              // D follows
              "d", "1", "e", /* this is also 1*/ "1", "f", "1", "g", "1", // G is important!
              "h", "1", "i", "1",

              /* Finally J */
              /* why not putting comment inside the call expression? */"j", "1");
  }
}