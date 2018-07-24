// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class Main {
  private static void test(List<String> list) {
      StringBuilder sb = new StringBuilder();
      for (String str: list) {
          if (str != null) {
              sb.append(str);
          }
      }
      String result = sb.toString();
    System.out.println(result);
  }

  public static void main(String[] args) {
    test(Arrays.asList("aa", "bbb", "c", null, "dd"));
  }
}