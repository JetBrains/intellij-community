// "Fix all 'Redundant 'Collection' operation' problems in file" "true"
import java.util.*;

class Test {
  void test(List<Integer> list) {
    list.add<caret>All(Collections.singleton(42));
    list.addAll(Collections.singletonList(42));
    list.addAll(List.of(42));
    list.addAll(Set.of(42));
  }
}