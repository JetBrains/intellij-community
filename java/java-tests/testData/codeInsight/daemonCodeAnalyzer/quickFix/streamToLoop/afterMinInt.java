// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;

public class Main {
  public static int test(List<String> strings) {
      boolean seen = false;
      int best = 0;
      for (String string : strings) {
          int i = string.length();
          if (!seen || i < best) {
              seen = true;
              best = i;
          }
      }
      return (seen ? OptionalInt.of(best) : OptionalInt.empty()).orElse(-1);
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList()));
    System.out.println(test(Arrays.asList("a", "bbb", "cc", "d")));
  }
}