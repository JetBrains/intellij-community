// "Replace with 'contains()'" "true-preview"
import java.util.*;

class Test {
  void test(List<String> list) {
    list.con<caret>tainsAll(Collections.singleton("foo"));
  }
}