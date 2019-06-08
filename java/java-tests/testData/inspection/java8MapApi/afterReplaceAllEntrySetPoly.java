// "Replace with 'replaceAll' method call" "true"

import java.util.HashMap;
import java.util.Map;

class Main {
  public void test() {
    Map<String, String> map = new HashMap<>();
    String defaultValue = "42";
    map.put("foo", "bar");
    /*1*//*8*//*9*//*2*//*3*//*4*//*5*/
      map/*6*/.replaceAll(/*7*//*10*/ (k, v) -> v +/*11*/ k +/*12*/ defaultValue + "baz");/*13*/
  }
}