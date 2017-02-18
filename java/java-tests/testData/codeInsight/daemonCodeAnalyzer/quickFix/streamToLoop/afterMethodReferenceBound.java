// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.List;

public class Main {
  private static String getString() {
    return "abc";
  }

  private static boolean test(List<String> strings) {
      String s = getString();
      for (String string : strings) {
          if (s.equals(string)) {
              return true;
          }
      }
      return false;
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList("a", "b", "c")));
  }
}