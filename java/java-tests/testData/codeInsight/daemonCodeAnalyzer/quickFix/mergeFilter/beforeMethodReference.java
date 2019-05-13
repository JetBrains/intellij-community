// "Merge filter chain" "true"

import java.util.List;

public class Main {
  void test(List<String> list) {
    list.stream().filte<caret>r(s -> s.trim().isEmpty()).filter(String::isEmpty).forEach(System.out::println);
  }
}