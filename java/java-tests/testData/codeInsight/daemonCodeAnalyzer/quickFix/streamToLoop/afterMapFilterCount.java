// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.List;

public class Main {
  private static long countNonEmpty(List<String> input) {
      long count = 0;
      for (String str : input) {
          String s = str.trim();
          if (!s.isEmpty()) {
              count++;
          }
      }
      return count;
  }

  public static void main(String[] args) {
    System.out.println(countNonEmpty(Arrays.asList("a", "", "b", "", "")));
  }
}