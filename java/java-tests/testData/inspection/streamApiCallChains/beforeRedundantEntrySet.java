// "Fix all 'Stream API call chain can be simplified' problems in file" "true"

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class Test {
  void test(Map<String, List<Integer>> map) {
    String str = map.entrySet().stream()
      .m<caret>ap(entry -> entry.getKey() + "!")
      .collect(Collectors.joining());
  }

  void test(Map<Object, List<Integer>> map) {
    String str = map.entrySet().stream()
      .map(entry -> entry.getKey() + ", ")
      .collect(Collectors.joining());
  }
}
