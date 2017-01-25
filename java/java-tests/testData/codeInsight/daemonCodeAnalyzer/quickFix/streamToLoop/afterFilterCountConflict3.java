// "Replace Stream API chain with loop" "true"

import java.util.Arrays;

public class Main {
  private static long countInRange(int... input) {
    int x = 1;
    int y = 2;
    int i = 3;
      long count = 0L;
      for (int x1 : input) {
          if (x1 > 0) {
              if (x1 < 10) {
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