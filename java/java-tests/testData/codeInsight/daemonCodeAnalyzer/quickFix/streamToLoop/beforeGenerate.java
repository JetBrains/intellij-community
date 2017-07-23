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
    return Stream.generate(() -> 10).flatMapToInt(x -> IntStream.range(0, x)).limit(33).summaryStatist<caret>ics();
  }

  public static void generateLimit() {
    int n1 = Stream.generate(() -> 500).limit(100).reduce(0, new SplittableRandom(1)::nextInt, Integer::sum);
    System.out.println(n1);
  }

  public static Integer getInt() {
    return 10;
  }

  public static IntSummaryStatistics generateMethodRef() {
    return Stream.generate(Main::getInt).flatMapToInt(x -> IntStream.range(0, x)).limit(33).summaryStatistics();
  }

  public static DoubleSummaryStatistics generateNested() {
    Random r = new Random();
    return IntStream.range(0, 10).mapToDouble(x -> x).flatMap(x -> DoubleStream.generate(() -> r.nextDouble()*x).limit((long) x)).summaryStatistics();
  }

  public static void main(String[] args) {
    System.out.println(generate());
    System.out.println(generateMethodRef());
    System.out.println(generateNested());
    generateLimit();
  }
}