// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class Main {
  public static String test(List<String> strings) {
      Comparator<String> comparator = Comparator.comparing(String::length);
      boolean seen = false;
      String best = null;
      for (String string : strings) {
          if (!seen || comparator.compare(string, best) > 0) {
              seen = true;
              best = string;
          }
      }
      return (seen ? Optional.of(best) : Optional.<String>empty()).orElse(null);
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList()));
    System.out.println(test(Arrays.asList("a", "bbb", "cc", "d", "eee")));
  }
}