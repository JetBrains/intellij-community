// "Replace Stream API chain with loop" "true"

import java.util.Arrays;

public class Main {
  private static long countInRange(int... input) {
    int x = 1;
      long count = 0L;
      for (int y : input) {
          if (y > 0) {
              if (y < 10) {
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