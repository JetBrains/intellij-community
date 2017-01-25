// "Replace with collect" "true"

import java.util.List;
import java.util.stream.Collectors;

public class Test {
  static String test(List<String> list) {
    String sb = "";
    if(!list.isEmpty()) {
        sb = list.stream().filter(s -> !s.isEmpty()).collect(Collectors.joining());
    }
    String s = sb;
    return s.trim();
  }
}