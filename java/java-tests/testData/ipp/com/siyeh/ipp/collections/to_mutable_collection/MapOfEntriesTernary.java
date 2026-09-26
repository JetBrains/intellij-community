import java.util.*;

class Test {

  void test(boolean b) {
    Map.Entry<String, String> oneEntry = Map.entry("foo", "baz");
    Map.Entry<String, String> anotherEntry = Map.entry("baz", "qux");
    System.out.println(Map.ofEntries(<caret>b ? oneEntry : anotherEntry));
  }
}