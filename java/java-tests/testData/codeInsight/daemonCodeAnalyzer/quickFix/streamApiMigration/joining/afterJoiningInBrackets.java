// "Collapse loop with stream 'collect()'" "true-preview"

import java.util.List;
import java.util.stream.Collectors;

public class Test {
  static String test(List<String> list) {
    String sb = list.stream().filter(s -> !s.isEmpty()).collect(Collectors.joining(",", "[", "]"));
      return sb.trim();
  }
}