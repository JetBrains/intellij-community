// "Replace Stream API chain with loop" "true"

import java.util.Arrays;

public class Main {
  private static long countInRange(int... input) {
    int x = 1;
    int i = 3;
      long result = 0L;
      for (int count : input) {
          if (count > 0) {
              if (count < 10) {
                  result++;
              }
          }
      }
      return result;
  }

  public static void main(String[] args) {
    System.out.println(countInRange(1, 2, 3, -1, -2));
  }
}