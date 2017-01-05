// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class Main {
  public static String test(List<String> strings, Comparator<CharSequence> comparator) {
      boolean seen = false;
      String best = null;
      Comparator<CharSequence> comparator1 = comparator.reversed();
      for (String string : strings) {
          if (!seen || comparator1.compare(string, best) < 0) {
              seen = true;
              best = string;
          }
      }
      return seen ? best : strings.toString();
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList(), Comparator.comparing(CharSequence::length)));
    System.out.println(test(Arrays.asList("a", "bbb", "cc", "d", "eee"), Comparator.comparing(CharSequence::length)));
  }
}