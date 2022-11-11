// "Replace with collect" "true-preview"

import java.util.List;
import java.util.stream.Collectors;

public class Test {
  static String test(List<String> list) {
    String sb = list.stream().filter(s -> !s.isEmpty()).map(String::trim).collect(Collectors.joining("", "asdsad" + "bar", ""));
      //comment
      return sb.length() == 0 ? null : sb;
  }
}