// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.List;
import java.util.OptionalDouble;

public class Main {
  public static double test(List<String> strings) {
      boolean seen = false;
      double best = 0;
      for (String string : strings) {
          double v = string.length();
          if (!seen || Double.compare(v, best) > 0) {
              seen = true;
              best = v;
          }
      }
      return (seen ? OptionalDouble.of(best) : OptionalDouble.empty()).orElse(-1);
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList()));
    System.out.println(test(Arrays.asList("a", "bbb", "cc", "d")));
  }
}