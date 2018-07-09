// "Fix all 'Comparator can be simplified' problems in file" "true"
import java.util.*;
import java.util.stream.*;

class Test {
  void test(List<String> list) {
    Collections.m<caret>ax(list, Comparator.reverseOrder());
    list.stream().min(String.CASE_INSENSITIVE_ORDER.reversed());
    list.stream().max(Collections.reverseOrder(String.CASE_INSENSITIVE_ORDER));
    Collector<String, ?, Optional<String>> c = Collectors.maxBy(Comparator.comparing(String::length, Comparator.reverseOrder()));
  }
}