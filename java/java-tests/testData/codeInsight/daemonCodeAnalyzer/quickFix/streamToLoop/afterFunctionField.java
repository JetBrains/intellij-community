// "Replace Stream API chain with loop" "true"

import java.util.List;
import java.util.function.Predicate;

public class Main {
  static Predicate<String> nonEmpty = s -> s != null && !s.isEmpty();

  private static long test(List<String> strings) {
      long count = 0L;
      for (String string : strings) {
          if (nonEmpty.test(string)) {
              count++;
          }
      }
      return count;
  }

  public static void main(String[] args) {
  }
}