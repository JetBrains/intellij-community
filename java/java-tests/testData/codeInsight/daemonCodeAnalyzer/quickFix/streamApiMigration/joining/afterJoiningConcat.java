// "Collapse loop with stream 'collect()'" "true-preview"

import java.util.List;
import java.util.stream.Collectors;

public class Test {
  static String test(List<String> list) {
    String sb;
    System.out.println("hello");
      sb = list.stream().filter(s -> !s.isEmpty()).map(String::trim).collect(Collectors.joining());
    String s = "Result: ";
    s += sb;
    System.out.println(s);
    return "[" + sb + "]";
  }
}