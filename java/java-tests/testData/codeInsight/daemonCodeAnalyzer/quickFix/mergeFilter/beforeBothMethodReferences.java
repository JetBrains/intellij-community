// "Merge filter chain" "true"

import java.util.List;
import java.util.Objects;

public class Main {
  void test(List<String> list) {
    list.stream().filter(Objects::nonNull).filt<caret>er(String::isEmpty).forEach(System.out::println);
  }
}