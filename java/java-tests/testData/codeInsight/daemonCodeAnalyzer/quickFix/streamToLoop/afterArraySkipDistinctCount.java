// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class Main {
  private static long test() {
      long count = 0L;
      Set<Integer> uniqueValues = new HashSet<>();
      long toSkip = 1;
      for (Integer integer: new Integer[]{1, 2, 3, 2, 3}) {
          if (toSkip > 0) {
              toSkip--;
              continue;
          }
          if (uniqueValues.add(integer)) {
              count++;
          }
      }
      return count;
  }

  public static void main(String[] args) {
    System.out.println(test());
  }
}