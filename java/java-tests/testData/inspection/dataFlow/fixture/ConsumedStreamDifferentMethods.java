import java.util.*;
import java.util.stream.*;

public class ConsumedStreamDifferentMethods {
  public static void test1() {
    Stream<String> stream = Stream.of("x");
    Stream<String> stringStream = stream.filter(t -> true);
    double random = Math.random();
    Stream<String> stringStream1 = <warning descr="Stream has already been linked or consumed">stream</warning>.map(t -> t);
  }

  public static void test2() {
    Stream<String> stream = Stream.empty();
    IntStream intStream = stream.mapToInt(t -> 1);
    double random = Math.random();
    LongStream longStream = <warning descr="Stream has already been linked or consumed">stream</warning>.mapToLong(t -> 1L);
  }

  public static void test3() {
    Stream<String> stream = Stream.generate(()->"x");
    IntStream intStream = stream.mapToInt(t -> 1);
    double random = Math.random();
    LongStream longStream = <warning descr="Stream has already been linked or consumed">stream</warning>.mapToLong(t -> 1L);
  }

  public static void test4() {
    Stream<String> stream = Stream.of("x");
    Stream<String> stream2 = stream.peek(t->{});
    double random = Math.random();
    Stream<String> stream3 = <warning descr="Stream has already been linked or consumed">stream</warning>.skip(1);
  }

  public static void test5() {
    IntStream stream = IntStream.of(1);
    IntStream stream2 = stream.filter(t -> true);
    double random = Math.random();
    OptionalInt min = <warning descr="Stream has already been linked or consumed">stream</warning>.min();
  }

  public static void test6() {
    IntStream stream = IntStream.empty();
    IntStream stream2 = stream.map(t -> 2);
    double random = Math.random();
    IntSummaryStatistics intSummaryStatistics = <warning descr="Stream has already been linked or consumed">stream</warning>.summaryStatistics();
  }

  public static void test7() {
    LongStream stream = LongStream.empty();
    IntStream intStream = stream.mapToInt(t -> 1);
    double random = Math.random();
    OptionalLong max = <warning descr="Stream has already been linked or consumed">stream</warning>.max();
  }

  public static void test8() {
    DoubleStream stream = DoubleStream.concat(DoubleStream.of(1), DoubleStream.of(2));
    Stream<String> stream2 = stream.mapToObj(String::valueOf);
    double random = Math.random();
    OptionalDouble average = <warning descr="Stream has already been linked or consumed">stream</warning>.average();
  }

  public static void test9() {
    ArrayList<String> strings = new ArrayList<>();
    strings.add("x");
    Stream<String> stream = strings.stream();
    long count = stream.count();
    double random = Math.random();
    Stream<String> stringStream = <warning descr="Stream has already been linked or consumed">stream</warning>.onClose(() -> {
    });
  }

  public static void test10() {
    int[] ints = new int[3];
    IntStream stream = Arrays.stream(ints);
    stream.close();
    double random = Math.random();
    IntStream distinct = <warning descr="Stream has already been linked or consumed">stream</warning>.distinct();
  }

  public static void test11() {
    ArrayList<String> strings = new ArrayList<>();
    strings.add("x");
    Spliterator<String> spliterator = strings.spliterator();
    Stream<String> stream = StreamSupport.stream(spliterator, false);
    List<String> collect = stream.collect(Collectors.toList());
    double random = Math.random();
    boolean parallel = stream.isParallel();
    Optional<String> any = <warning descr="Stream has already been linked or consumed">stream</warning>.findAny();
  }

  public static void test12() {
    ArrayList<String> strings = new ArrayList<>();
    strings.add("x");
    Stream<String> stream = strings.stream().filter(t->true);
    List<String> collect = stream.collect(Collectors.toList());
    double random = Math.random();
    boolean parallel = stream.isParallel();
    Optional<String> any = <warning descr="Stream has already been linked or consumed">stream</warning>.findAny();
  }
}
