// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

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

  private static long checkClosed(int start, double val) {
      long count = 0L;
      int bound = start * 200;
      for (int x = start; x <= bound; x++) {
          double v = 1.0 / x;
          if (v < val) {
              count++;
          }
      }
      return count;
  }

  public static void main(String[] args) {
    System.out.println(check(1, 100, 0.04));
    System.out.println(checkClosed(2, 0.04));
  }
}