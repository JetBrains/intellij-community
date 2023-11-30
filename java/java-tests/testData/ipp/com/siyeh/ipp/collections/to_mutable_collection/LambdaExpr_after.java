import java.util.*;
import java.util.concurrent.Callable;

class Test {
  void test() {
    Callable<Map<String, String>> c = () -> {
        Map<String, String> stringStringMap = new HashMap<>();
        stringStringMap.put("foo", "bar");
        return stringStringMap;
    };
  }
}