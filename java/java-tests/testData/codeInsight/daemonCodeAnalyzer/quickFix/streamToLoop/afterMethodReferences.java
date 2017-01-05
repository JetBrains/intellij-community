// "Replace Stream API chain with loop" "true"

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

  public static void main(String[] args) {
    test(Arrays.asList("a", "b", "xyz"));
  }
}