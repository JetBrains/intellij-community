// "Replace Stream API chain with loop" "true"

import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main<T> {
  void test() {
    Integer totalLength = Stream.of("a", "bb", "ccc").co<caret>llect(Collectors.reducing(0, String::length, Integer::sum));
  }
}