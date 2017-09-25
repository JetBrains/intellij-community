// "Replace with collect" "true"

import java.util.List;
import java.util.stream.Collectors;

public class Test {
  static String test(List<String> list) {
    String sb;
    boolean first = true;
      sb = list.stream().map(s -> ", " + s.trim()).collect(Collectors.joining());
    return sb.length() == 0 ? null : sb;
  }
}