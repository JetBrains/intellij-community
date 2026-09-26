import java.util.*;

class Test {

  volatile Map<String, String> map;

  void test() {
      Map<String, String> stringStringMap = new HashMap<>();
      stringStringMap.put("foo", "bar");
      map = stringStringMap;
  }
}