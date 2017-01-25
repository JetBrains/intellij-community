// "Replace Stream API chain with loop" "true"

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Test {
  static void test(List<String> list) {
    Map<Boolean, Double> map1 = list.stream()
      .col<caret>lect(Collectors.partitioningBy(String::isEmpty, Collectors.summingDouble(String::length)));
  }
}