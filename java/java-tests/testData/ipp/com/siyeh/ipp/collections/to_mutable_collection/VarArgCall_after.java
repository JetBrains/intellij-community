import java.util.*;

class Test {

  void test() {
    Map.Entry<?, ?>[] e = {Map.entry("foo", "bar")};
      Map<Object, Object> x = new HashMap<>(Map.ofEntries(e));
      System.out.println(x);
  }
}