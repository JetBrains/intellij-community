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
          double v = x;
          for (long count = (long) v; count > 0; count--) {
              double v1 = r.nextDouble() * v;
              stat.accept(v1);
          }
      }
      return stat;
  }

  public static void main(String[] args) {
    System.out.println(test());
  }
}