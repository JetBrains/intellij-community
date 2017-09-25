// "Replace with collect" "true"

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Test {
  static String test(List<String> list) {
    String sb = "";
      if (!list.isEmpty()) {
          sb = list.stream().filter(s -> !s.isEmpty()).map(s -> String.valueOf(s.length())).collect(Collectors.joining("\"", "{", ""));
    }
    return sb.trim();
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList("abc", "", "xyz", "argh")));
  }
}