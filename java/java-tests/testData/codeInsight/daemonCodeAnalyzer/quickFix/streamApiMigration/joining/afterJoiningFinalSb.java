// "Replace with collect" "true"

import java.util.List;
import java.util.stream.Collectors;

public class Test {
  static String test(List<String> list) {
      final String sb = list.stream().filter(s -> !s.isEmpty()).map(String::trim).collect(Collectors.joining("", "ctor" + "first", ""));
      return sb.length() == 0 ? null : sb;
  }
}