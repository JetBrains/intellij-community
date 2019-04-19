// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.List;

public class Main {
  private static String test(List<String> list) {
    if (list == null) {
      return null;
    } else {
      return list.stream().filter(str -> str.contains("x")).find<caret>First().orElse(null); // comment 
    }
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList("a", "b", "syz")));
  }
}