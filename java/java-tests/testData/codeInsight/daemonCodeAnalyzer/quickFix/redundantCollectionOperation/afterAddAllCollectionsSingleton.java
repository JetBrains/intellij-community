// "Fix all 'Redundant 'Collection' operation' problems in file" "true"
import java.util.*;

class Test {
  void test(List<Integer> list) {
    list.add(42);
    list.add(42);
    list.add(42);
    list.add(42);
  }
}