// "Replace Stream API chain with loop" "true"

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Test {
  static void test(List<String> list) {
    Map<Integer, Double> map4 = list.stream().co<caret>llect(Collectors.groupingBy(String::length, Collectors.summingDouble(String::length)));
  }
}