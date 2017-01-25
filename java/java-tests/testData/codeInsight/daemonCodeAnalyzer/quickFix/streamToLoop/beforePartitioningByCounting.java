// "Replace Stream API chain with loop" "true"

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Test {
  static void test(List<String> list) {
    Map<Boolean, Long> map2 = list.stream()
      .co<caret>llect(Collectors.partitioningBy(String::isEmpty, Collectors.counting()));
  }
}