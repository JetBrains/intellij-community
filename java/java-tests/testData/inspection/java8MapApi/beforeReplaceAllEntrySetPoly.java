// "Replace with 'replaceAll' method call" "true"

import java.util.HashMap;
import java.util.Map;

class Main {
  public void test() {
    Map<String, String> map = new HashMap<>();
    String defaultValue = "42";
    map.put("foo", "bar");
    /*1*/for<caret> /*2*/(Map.Entry<String, String> /*3*/entry : map./*4*/entrySet()) {
      /*5*/map/*6*/.put(/*7*/entry./*8*/getKey(/*9*/),/*10*/ entry.getValue() +/*11*/ entry.getKey() +/*12*/ defaultValue + "baz");/*13*/
    }
  }
}