// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class Main {
  private static void test(List<String> names) {
      for (String name : names) {
          if (name != null) {
              System.out.println(name);
          }
      }
  }

  private static String getString() {
    return "abc";
  }

  private static boolean testBound(List<String> strings) {
      String s = getString();
      for (String string : strings) {
          if (s.equals(string)) {
              return true;
          }
      }
      return false;
  }

  public static void main(String[] args) {
    test(Arrays.asList("a", "b", "xyz"));
    System.out.println(testBound(Arrays.asList("a", "b", "c")));
  }
}