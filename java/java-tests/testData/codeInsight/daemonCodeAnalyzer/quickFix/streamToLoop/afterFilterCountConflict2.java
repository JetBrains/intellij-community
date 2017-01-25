// "Replace Stream API chain with loop" "true"

import java.util.Arrays;

public class Main {
  private static long countInRange(int... input) {
    int x = 1;
    int y = 2;
      long count = 0L;
      for (int i : input) {
          if (i > 0) {
              if (i < 10) {
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