// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.List;

public class Main {
  private static void test(List<String> list) {
      for (String x : list) {
          if (x != null) {
              System.out.println(x);
          }
      }
  }

  public static void main(String[] args) {
    test(Arrays.asList("a", "b", "xyz"));
  }
}