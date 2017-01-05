// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main<T> {
  void test() {
      Integer totalLength = 0;
      for (String s : Arrays.asList("a", "bb", "ccc")) {
          Integer length = s.length();
          totalLength = totalLength + length;
      }
  }
}