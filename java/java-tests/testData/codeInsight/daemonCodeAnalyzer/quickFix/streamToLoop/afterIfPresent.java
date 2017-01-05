// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.List;

public class Main {
  private static void test(List<String> list) {
      for (String str : list) {
          if (str.contains("x")) {
              System.out.println(str);
              break;
          }
      }
  }

  public static void main(String[] args) {
    test(Arrays.asList("a", "b", "syz"));
  }
}