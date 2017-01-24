// "Replace Stream API chain with loop" "true"

import java.util.Arrays;

public class Main {
  private static long countInRange(int... input) {
      long count = 0L;
      for (int x : input) {
          if (x > 0) {
              if (x < 10) {
                  count++;
              }
          }
      }
      return count;
  }

  public static void main(String[] args) {
    System.out.println(countInRange(1, 2, 3, -1, -2));
  }
}