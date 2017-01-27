// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Main {
  public static IntSummaryStatistics test() {
    return Stream.iterate("", x -> x + "a").limit(20).mapToInt(x -> x.length()).summaryStati<caret>stics();
  }

  public static List<String> testUseName() {
    return Stream.iterate("", x -> x /* add "a" */ + "a").limit(/*limit*/20).collect(Collectors.toList());
  }

  public static IntSummaryStatistics testNested() {
    return IntStream.range(0, 20).flatMap(limit -> Stream.iterate("", x -> x + limit).limit(limit).mapToInt(x -> x.length())).summaryStatistics();
  }

  private static List<String> testNestedUseName() {
    return IntStream.range(0, 20).mapToObj(x -> x)
      .flatMap(x -> Stream.iterate("", str -> "a"+str).limit(x))
      .collect(Collectors.toList());
  }

  public static IntSummaryStatistics testNestedRename() {
    return IntStream.range(0, 20).filter(x -> x > 2).flatMap(limit -> Stream.iterate(String.valueOf(limit), x -> x + limit).limit(limit).mapToInt(x -> x.length())).summaryStatistics();
  }

  public static void main(String[] args) {
    System.out.println(test());
    System.out.println(testUseName());
    System.out.println(testNested());
    System.out.println(testNestedRename());
    System.out.println(String.join("|", testNestedUseName()).length());
  }
}