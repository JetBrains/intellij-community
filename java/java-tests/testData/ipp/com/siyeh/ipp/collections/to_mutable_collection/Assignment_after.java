import java.util.*;

class Test {

  void foo(Map<String, String> map) {
      /*2*/
      /*3*/
      map = /*1*/(new HashMap<>())/*4*/;
      map.put("foo", "bar");
      process(map);
  }

  void process(Map<String, String> model) {
  }
}