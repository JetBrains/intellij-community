// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.List;

public class Main {
  private static boolean test(List<String> list) {
      for (String s : list) {
          if (s.trim() != s.toLowerCase()) {
              return false;
          }
      }
      return true;
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList("a", "b", "c")));
  }
}