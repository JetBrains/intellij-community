// "Replace Stream API chain with loop" "true"

import java.util.stream.IntStream;

public class Main {
  private static long check(int start, double val) {
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
    System.out.println(check(2, 0.04));
  }
}