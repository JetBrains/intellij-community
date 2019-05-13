// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.DoubleSummaryStatistics;
import java.util.IntSummaryStatistics;
import java.util.Random;
import java.util.SplittableRandom;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Main {
  public static IntSummaryStatistics generate() {
      IntSummaryStatistics stat = new IntSummaryStatistics();
      long limit = 33;
      OUTER:
      while (true) {
          Integer x = 10;
          for (int i = 0; i < x; i++) {
              if (limit-- == 0) break OUTER;
              stat.accept(i);
          }
      }
      return stat;
  }

  public static void generateLimit() {
      Integer acc = 0;
      SplittableRandom splittableRandom = new SplittableRandom(1);
      for (long count = 100; count > 0; count--) {
          Integer integer = 500;
          acc = splittableRandom.nextInt(acc, integer);
      }
      int n1 = acc;
    System.out.println(n1);
  }

  public static Integer getInt() {
    return 10;
  }

  public static IntSummaryStatistics generateMethodRef() {
      IntSummaryStatistics stat = new IntSummaryStatistics();
      long limit = 33;
      OUTER:
      while (true) {
          Integer x = getInt();
          for (int i = 0; i < x; i++) {
              if (limit-- == 0) break OUTER;
              stat.accept(i);
          }
      }
      return stat;
  }

  public static DoubleSummaryStatistics generateNested() {
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
    System.out.println(generate());
    System.out.println(generateMethodRef());
    System.out.println(generateNested());
    generateLimit();
  }
}