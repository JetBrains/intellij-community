// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.List;

public class Main {
  private static boolean test(List<String> list) {
    return list.stream().al<caret>lMatch(s -> s.trim() == s.toLowerCase());
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList("a", "b", "c")));
  }
}