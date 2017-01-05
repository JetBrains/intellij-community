// "Replace Stream API chain with loop" "true"

import java.util.*;

public class Main {
  public static OptionalDouble test(List<String> strings) {
      boolean seen = false;
      double best = 0;
      for (String string : strings) {
          double length = string.length();
          if (!seen || Double.compare(length, best) > 0) {
              seen = true;
              best = length;
          }
      }
      return seen ? OptionalDouble.of(best) : OptionalDouble.empty();
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList()));
    System.out.println(test(Arrays.asList("a", "bbb", "cc", "d")));
  }
}