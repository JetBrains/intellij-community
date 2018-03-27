// "Merge filter chain" "true"

import java.util.List;

public class Main {
  void test(List<String> list) {
    list.stream().filter(s -> s.trim().isEmpty() && s.isEmpty()).forEach(System.out::println);
  }
}