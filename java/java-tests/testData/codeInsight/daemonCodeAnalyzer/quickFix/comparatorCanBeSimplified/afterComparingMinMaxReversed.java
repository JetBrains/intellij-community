// "Fix all 'Comparator can be simplified' problems in file" "true"
import java.util.*;
import java.util.stream.*;

class Test {
  void test(List<String> list) {
    Collections.min(list, Comparator.naturalOrder());
    list.stream().max(String.CASE_INSENSITIVE_ORDER);
    list.stream().min(String.CASE_INSENSITIVE_ORDER);
    Collector<String, ?, Optional<String>> c = Collectors.minBy(Comparator.comparing(String::length));
  }
}