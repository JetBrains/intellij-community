// "Collapse loop with stream 'collect()'" "true-preview"

import java.util.List;
import java.util.stream.Collectors;

public class Test {
  private static String work2(List<String> strs) {
    String sb = strs.stream().collect(Collectors.joining(",", "{", "}"));
      return sb;
  }
}