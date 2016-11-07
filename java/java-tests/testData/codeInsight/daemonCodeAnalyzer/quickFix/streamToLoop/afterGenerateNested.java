// "Replace Stream API chain with loop" "true"

import java.util.DoubleSummaryStatistics;
import java.util.Random;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

public class Main {
  public static DoubleSummaryStatistics test() {
    Random r = new Random();
      DoubleSummaryStatistics stat = new DoubleSummaryStatistics();
      for (int x = 0; x < 10; x++) {
          double x1 = x;
          long limit = (long) x1;
          while (true) {
              double v = r.nextDouble() * x1;
              if (limit-- == 0) break;
              stat.accept(v);
          }
      }
      return stat;
  }

  public static void main(String[] args) {
    System.out.println(test());
  }
}