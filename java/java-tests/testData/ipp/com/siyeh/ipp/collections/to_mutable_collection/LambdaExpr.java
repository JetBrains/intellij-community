import java.util.*;
import java.util.concurrent.Callable;

class Test {
  void test() {
    Callable<Map<String, String>> c = () -> Collections.singletonMap<caret>("foo", "bar");
  }
}