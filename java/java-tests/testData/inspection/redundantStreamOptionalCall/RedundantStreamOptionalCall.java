import java.util.*;
import java.util.function.Function;
import java.util.stream.*;

public class RedundantStreamOptionalCall {
  public void test() {
    List<Integer> list = Stream.of(1, 2, 3).<warning descr="Redundant 'sorted()' call: there is a subsequent 'sorted()' call in the chain">sorted(Comparator.reverseOrder())</warning>
      .filter(x -> x > 0).sorted().collect(Collectors.toList());
    if(Stream.of(1, 2, 3).<warning descr="Redundant 'sorted()' call: subsequent 'allMatch()' call doesn't depend on the sort order">sorted()</warning>.filter(x -> x > 0).allMatch(x -> x < 10)) {
      return;
    }
    if(Stream.of("foo", "bar", "baz").<warning descr="Redundant 'sorted()' call: subsequent 'count()' call doesn't depend on the sort order">sorted(String.CASE_INSENSITIVE_ORDER)</warning>.count() > 0) {
      return;
    }
    long first = Stream.of(1, 2, 3).distinct().sorted().skip(1).limit(2).<warning descr="Redundant 'distinct()' call: there is a previous 'distinct()' call in the chain">distinct()</warning>
      .findFirst().orElse(0);
    Object[] objects = Stream.of(1, 2, 3).distinct().map(x -> x*2).distinct().toArray();
    Object[] objects2 = Stream.of(1, 2, 3).distinct().<warning descr="Redundant 'filter()' call: predicate is always 'true'">filter(x -> true)</warning>.
      <warning descr="Redundant 'distinct()' call: there is a previous 'distinct()' call in the chain">distinct()</warning>.toArray();
    Object xyz = Optional.of(123).<warning descr="Redundant 'map()' call">map(integer -> Integer.valueOf(integer))</warning>.orElse(null);
    Object xyz2 = Optional.of(123).<warning descr="Redundant 'map()' call">map(Integer::valueOf)</warning>.orElse(null);
    Object xyz3 = Optional.of(123).<warning descr="Redundant 'map()' call">map(Integer::intValue)</warning>.orElse(null);
    Object xyz4 = Optional.of(123).map(Integer::longValue).orElse(null);
    Object xyz5 = IntStream.of(123).<warning descr="Redundant 'map()' call">map(i -> Integer.valueOf(i))</warning>.count();
    Object xyz6 = Stream.of(123).<warning descr="Redundant 'map()' call">map(i -> Integer.valueOf(i))</warning>.count();
    Optional.of("xyz").<warning descr="Redundant 'flatMap()' call">flatMap(Optional::ofNullable)</warning>.ifPresent(System.out::println);
    Optional.of("xyz").<warning descr="Redundant 'flatMap()' call">flatMap(Optional::of)</warning>.ifPresent(System.out::println);

    Optional.of(123).<warning descr="Redundant 'filter()' call: predicate is always 'true'">filter(x -> true)</warning>.ifPresent(System.out::println);
    double avg = IntStream.range(0, 100).distinct()
                   .asLongStream().<warning descr="Redundant 'distinct()' call: there is a previous 'distinct()' call in the chain">distinct()</warning>.average().orElse(0);

    LongStream.range(0, 100).<warning descr="Redundant 'parallel()' call: there is a subsequent 'sequential()' call which overrides this call">parallel()</warning>
      .boxed().map(x -> x*2).sorted().sequential().forEach(System.out::println);
    Stream.of(0, 100).map(x -> x*2).<warning descr="Redundant 'parallel()' call: there is a subsequent 'parallel()' call which overrides this call">parallel()</warning>
      .filter(x -> x > 0).sorted().parallel().forEach(System.out::println);
    IntStream.of(0, 100).map(x -> x*2).<warning descr="Redundant 'sequential()' call: there is a subsequent 'sequential()' call which overrides this call">sequential()</warning>
      .filter(x -> x > 0).distinct().sequential().forEach(System.out::println);
    Stream.of(0, 100).map(x -> x*2).<warning descr="Redundant 'sequential()' call: there is a subsequent 'parallel()' call which overrides this call">sequential()</warning>
      .filter(x -> x > 0).limit(10).parallel().forEach(System.out::println);
    Stream.of("xyz").parallel().<warning descr="Redundant 'sorted()' call: stream contains at most one element">sorted()</warning>.collect(Collectors.toList()).stream().<warning descr="Redundant 'sequential()' call: the stream was created via 'stream()', so it's already sequential">sequential()</warning>.forEach(System.out::println);

    IntStream.range(0, 100).unordered().filter(x -> x > 50).<warning descr="Redundant 'unordered()' call: there is a previous 'unordered()' call in the chain">unordered()</warning>.forEach(System.out::println);
    IntStream.range(0, 100).unordered().filter(x -> x > 50).sorted().unordered().forEach(System.out::println);

    Collection<String> collection = Arrays.asList("foo", "foo", "bar");
    Set<String> set1 = collection.stream().<warning descr="Redundant 'distinct()' call: elements will be distinct anyway when collected to a Set">distinct()</warning>.collect(Collectors.toSet());
    Set<String> set2 = collection.stream().<warning descr="Redundant 'sorted()' call: subsequent 'toSet()' call doesn't depend on the sort order">sorted()</warning>.collect(Collectors.toSet());
    Set<String> set3 = collection.stream().<warning descr="Redundant 'sorted()' call: subsequent 'toSet()' call doesn't depend on the sort order">sorted()</warning>.<warning descr="Redundant 'distinct()' call: elements will be distinct anyway when collected to a Set">distinct()</warning>.collect(Collectors.toSet());
    Set<String> set4 = collection.stream().<warning descr="Redundant 'distinct()' call: elements will be distinct anyway when collected to a Set">distinct()</warning>.<warning descr="Redundant 'sorted()' call: subsequent 'toSet()' call doesn't depend on the sort order">sorted()</warning>.collect(Collectors.toSet());
    List<String> list1 = collection.stream().distinct().collect(Collectors.toList());
    List<String> list2 = collection.stream().sorted().collect(Collectors.toList());
    Map<Integer, String> map1 = collection.stream().<warning descr="Redundant 'sorted()' call: subsequent 'toMap()' call doesn't depend on the sort order">sorted()</warning>.collect(Collectors.toMap(Integer::valueOf, x -> x));
    Map<Integer, String> map2 = collection.stream().sorted().collect(Collectors.toMap(Integer::valueOf, x -> x, (a,b) ->a, LinkedHashMap::new));
    Set<String> set5 = collection.stream().<warning descr="Redundant 'sorted()' call: subsequent 'toCollection()' call doesn't depend on the sort order">sorted()</warning>.collect(Collectors.toCollection(HashSet::new));
    Set<String> set6 = collection.stream().<warning descr="Redundant 'sorted()' call: subsequent 'toCollection()' call doesn't depend on the sort order">sorted()</warning>.collect(Collectors.toCollection(() -> new HashSet<>()));
    Set<String> set6a = collection.stream().sorted().collect(Collectors.toCollection(() -> new LinkedHashSet<>()));
    Set<String> set7 = collection.stream().<warning descr="Redundant 'distinct()' call: elements will be distinct anyway when collected to a Set">distinct()</warning>.collect(Collectors.toCollection(HashSet::new));
    Set<String> set8 = collection.stream().<warning descr="Redundant 'distinct()' call: elements will be distinct anyway when collected to a Set">distinct()</warning>.collect(Collectors.toCollection(() -> new HashSet<>()));
    Set<String> set8a = collection.stream().<warning descr="Redundant 'distinct()' call: elements will be distinct anyway when collected to a Set">distinct()</warning>.collect(Collectors.toCollection(() -> new LinkedHashSet<>()));

    IntStream.of(123).mapToObj(String::valueOf).<warning descr="Redundant 'sorted()' call: stream contains at most one element">sorted()</warning>;
    LongStream.of(123).filter(x -> x > 0).mapToObj(String::valueOf).<warning descr="Redundant 'distinct()' call: stream contains at most one element">distinct()</warning>;
    LongStream.of(123).filter(x -> x > 0).mapToObj(String::valueOf).flatMap(x -> Stream.of(x, x+x)).distinct();
    Stream.of("foo").flatMap(x -> Stream.of(x, x)).parallel();
    Stream.of("foo").flatMap(x -> Stream.of(x, x)).<warning descr="Redundant 'parallel()' call: stream created from single element will not be parallelized">parallel()</warning>.forEach(System.out::println);

    Stream.of("foo", "bar", "baz").<warning descr="Redundant 'sorted()' call: there is a subsequent 'sorted()' call in the chain">sorted()</warning>.sorted(Comparator.<String>naturalOrder().reversed());
    Stream.of("foo", "bar", "baz").sorted().sorted(Comparator.comparing(x -> x.charAt(0) == 'b'));
    Stream.of("foo", "bar", "baz").<warning descr="Redundant 'sorted()' call: there is a subsequent 'sorted()' call in the chain">sorted()</warning>.sorted(Comparator.comparing(x -> x.charAt(0) == 'b')).sorted(Comparator.reverseOrder());

    Stream.of("foo", "bar", "baz").<warning descr="Redundant 'sorted()' call: subsequent 'max()' call doesn't depend on the sort order">sorted(String.CASE_INSENSITIVE_ORDER)</warning>.max(String.CASE_INSENSITIVE_ORDER.reversed());
    Stream.of("foo", "bar", "baz").<warning descr="Redundant 'sorted()' call: subsequent 'min()' call doesn't depend on the sort order">sorted(String.CASE_INSENSITIVE_ORDER)</warning>.min(String.CASE_INSENSITIVE_ORDER);
    Stream.of("foo", "bar", "baz").sorted(String.CASE_INSENSITIVE_ORDER).min(Comparator.naturalOrder());
  }
  
  public static void flatMapStreamOf(String[] arr1, String[] arr2) {
    Stream.of(1,2,3,4,5).<warning descr="Redundant 'flatMap()' call">flatMap(Stream::of)</warning>.toArray();
    Stream.of(1,2,3,4,5).<warning descr="Redundant 'flatMap()' call">flatMap(t -> Stream.of(t))</warning>.toArray();
    Stream.of(arr1, arr2).flatMap(Stream::of).toArray();
  }

  void fromCollection(Collection<String> c) {
    c.parallelStream().sorted().<warning descr="Redundant 'parallel()' call: the stream was created via 'parallelStream()', so it's already parallel">parallel()</warning>.forEach(System.out::println);
    c.stream().<warning descr="Redundant 'sequential()' call: the stream was created via 'stream()', so it's already sequential">sequential()</warning>.forEach(System.out::println);
    // strange but not redundant
    c.parallelStream().sequential().forEach(System.out::println);
    c.stream().parallel().forEach(System.out::println);
    // another stream
    c.parallelStream().sorted().findFirst().get().chars().parallel().forEach(System.out::println);
  }
  
  void dropWhileAndSort() {
    List<String> collect = Stream.of("E", "D", "C", "B")
      .sorted()
      .dropWhile(x -> !x.equals("D"))
      .sorted(Comparator.reverseOrder())
      .collect(Collectors.toList());
  }

  void parallelSorted(Collection<String> c) {
    c.stream().sorted().forEach(x -> {});
    c.parallelStream().<warning descr="Redundant 'sorted()' call: subsequent 'forEach()' operation doesn't depend on the sort order for parallel streams">sorted()</warning>.forEach(x -> {});
    c.parallelStream().distinct().<warning descr="Redundant 'sorted()' call: subsequent 'forEach()' operation doesn't depend on the sort order for parallel streams">sorted()</warning>.forEach(x -> {});
    c.stream().parallel().<warning descr="Redundant 'sorted()' call: subsequent 'forEach()' operation doesn't depend on the sort order for parallel streams">sorted()</warning>.forEach(x -> {});
    c.stream().parallel().sorted().forEachOrdered(x -> {});
    c.stream().parallel().sorted().filter(x -> !x.isEmpty()).findFirst();
    c.stream().parallel().<warning descr="Redundant 'sorted()' call: subsequent 'findAny()' operation doesn't depend on the sort order for parallel streams">sorted()</warning>.filter(x -> !x.isEmpty()).findAny();
    c.stream().parallel().distinct().<warning descr="Redundant 'sorted()' call: subsequent 'forEach()' operation doesn't depend on the sort order for parallel streams">sorted()</warning>.forEach(x -> {});
    c.stream().<warning descr="Redundant 'parallel()' call: there is a subsequent 'sequential()' call which overrides this call">parallel()</warning>.distinct().sequential().sorted().forEach(x -> {});
    c.stream().<warning descr="Redundant 'parallel()' call: there is a subsequent 'sequential()' call which overrides this call">parallel()</warning>.sorted().sequential().forEach(x -> {});
  }

  public static Stream<SomeClazz> fun1(Stream<Stream<SomeClazz>> objectStreams) {
    return objectStreams.<Stream<SomeClazz>>map(Stream::distinct).flatMap(Function.identity());
  }
  public static Stream<SomeClazz> fun2(Stream<Stream<SomeClazz>> objectStreams) {
    return objectStreams.map(Stream::distinct).<warning descr="Redundant 'flatMap()' call: previous 'map()' call can replace the 'flatMap()' step">flatMap(Function.identity())</warning>;
  }
  public static Stream<SomeClazz> fun3(Stream<Stream<SomeClazz>> objectStreams) {
    return objectStreams.<warning descr="Redundant 'map()' call">map(Function.identity())</warning>.flatMap(Function.identity());
  }
  private static class SomeClazz {
  }
}
