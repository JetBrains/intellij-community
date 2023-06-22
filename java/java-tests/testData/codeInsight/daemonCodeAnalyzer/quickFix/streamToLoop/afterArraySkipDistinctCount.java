// "Replace Stream API chain with loop" "true-preview"

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class Main {
  private static long test() {
      long count = 0L;
      Set<Integer> uniqueValues = new HashSet<>();
      boolean first = true;
      for (Integer i : new Integer[]{1, 2, 3, 2, 3}) {
          if (first) {
              first = false;
              continue;
          }
          if (uniqueValues.add(i)) {
              count++;
          }
      }
      return count;
  }

  public static void main(String[] args) {
    System.out.println(test());
  }
}