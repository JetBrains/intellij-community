// "Replace Stream API chain with loop" "true"

import java.util.stream.IntStream;

public class Main {
  private static long check(int start, int stop, double v) {
      long count = 0L;
      for (int x = start; x < stop; x++) {
          double x1 = 1.0 / x;
          if (x1 < v) {
              count++;
          }
      }
      return count;
  }

  public static void main(String[] args) {
    System.out.println(check(1, 100, 0.04));
  }
}