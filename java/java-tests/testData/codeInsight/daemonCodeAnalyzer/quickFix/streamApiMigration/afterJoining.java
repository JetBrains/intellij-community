// "Replace with collect" "true"

import java.util.List;
import java.util.stream.Collectors;

public class Test {
  static String test(List<String> list) {
    String sb;
    System.out.println("hello");
      sb = list.stream().filter(s -> !s.isEmpty()).map(String::trim).collect(Collectors.joining());
    return sb.length() == 0 ? null : sb;
  }
}