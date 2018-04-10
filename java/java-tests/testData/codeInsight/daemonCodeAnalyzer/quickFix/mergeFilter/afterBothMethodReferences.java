// "Merge filter chain" "true"

import java.util.List;
import java.util.Objects;

public class Main {
  void test(List<String> list) {
    list.stream().filter(o -> Objects.nonNull(o) && o.isEmpty()).forEach(System.out::println);
  }
}