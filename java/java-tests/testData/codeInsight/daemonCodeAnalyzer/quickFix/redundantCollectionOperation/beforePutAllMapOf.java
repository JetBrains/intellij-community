// "Replace with 'put()'" "true-preview"
import java.util.*;

class Test {
  void test(Map<Integer, String> map) {
    map.put<caret>All(Map.of(1, "one"));
  }
}