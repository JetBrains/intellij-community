// "Replace with collect" "true-preview"

import java.util.List;
import java.util.stream.Collectors;

public class Test {
  static String test(List<Integer> list) {
    String sb = "";
    if(!list.isEmpty()) {
        sb = list.stream().filter(i -> i != 0).map(String::valueOf).collect(Collectors.joining());
    }
    String s = sb;
    return s.trim();
  }
}