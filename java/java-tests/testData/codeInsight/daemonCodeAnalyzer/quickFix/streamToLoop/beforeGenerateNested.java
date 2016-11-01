// "Replace Stream API chain with loop" "true"

import java.util.DoubleSummaryStatistics;
import java.util.Random;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

public class Main {
  public static DoubleSummaryStatistics test() {
    Random r = new Random();
    return IntStream.range(0, 10).mapToDouble(x -> x).flatMap(x -> DoubleStream.generate(() -> r.nextDouble()*x).limit((long) x)).summaryStat<caret>istics();
  }

  public static void main(String[] args) {
    System.out.println(test());
  }
}