// "Replace with 'add()'" "true"
import java.util.*;

class Test {
  void test(List<Integer> list) {
    list.add<caret>All(Collections.singleton(42));
  }
}