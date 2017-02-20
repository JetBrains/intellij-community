import java.util.*;
import java.util.stream.*;

public class RedundantStreamOptionalCall {
  public void test() {
    List<Integer> list = Stream.of(1, 2, 3).<warning descr="Redundant 'sorted' call: subsequent 'sorted' call makes sorting useless">sorted(Comparator.reverseOrder())</warning>
      .filter(x -> x > 0).sorted().collect(Collectors.toList());
    if(Stream.of(1, 2, 3).<warning descr="Redundant 'sorted' call: subsequent 'allMatch' call makes sorting useless">sorted()</warning>.filter(x -> x > 0).allMatch(x -> x < 10)) {
      return;
    }
    if(Stream.of("foo", "bar", "baz").<warning descr="Redundant 'sorted' call: subsequent 'count' call makes sorting useless">sorted(String.CASE_INSENSITIVE_ORDER)</warning>.count() > 0) {
      return;
    }
    long first = Stream.of(1, 2, 3).distinct().sorted().skip(1).limit(2).<warning descr="Redundant 'distinct' call: there already was a 'distinct' call in the chain">distinct()</warning>
      .findFirst().orElse(0);
    Object[] objects = Stream.of(1, 2, 3).distinct().map(x -> x*2).distinct().toArray();
    Object[] objects2 = Stream.of(1, 2, 3).distinct().<warning descr="Redundant 'filter' call: predicate is always true">filter(x -> true)</warning>.
      <warning descr="Redundant 'distinct' call: there already was a 'distinct' call in the chain">distinct()</warning>.toArray();
    Object xyz = Optional.of(123).<warning descr="Redundant 'map' call">map(integer -> Integer.valueOf(integer))</warning>.orElse(null);
    Object xyz2 = Optional.of(123).<warning descr="Redundant 'map' call">map(Integer::valueOf)</warning>.orElse(null);
    Object xyz3 = Optional.of(123).<warning descr="Redundant 'map' call">map(Integer::intValue)</warning>.orElse(null);
    Object xyz4 = Optional.of(123).map(Integer::longValue).orElse(null);
    Object xyz5 = IntStream.of(123).<warning descr="Redundant 'map' call">map(i -> Integer.valueOf(i))</warning>.count();
    Object xyz6 = Stream.of(123).<warning descr="Redundant 'map' call">map(i -> Integer.valueOf(i))</warning>.count();

    Optional.of(123).<warning descr="Redundant 'filter' call: predicate is always true">filter(x -> true)</warning>.ifPresent(System.out::println);
    double avg = IntStream.range(0, 100).distinct()
                   .asLongStream().<warning descr="Redundant 'distinct' call: there already was a 'distinct' call in the chain">distinct()</warning>.average().orElse(0);

    LongStream.range(0, 100).<warning descr="Redundant 'parallel' call: there's subsequent 'sequential' call which overrides this call">parallel()</warning>
      .boxed().map(x -> x*2).sorted().sequential().forEach(System.out::println);
    Stream.of(0, 100).map(x -> x*2).<warning descr="Redundant 'parallel' call: there's subsequent 'parallel' call which overrides this call">parallel()</warning>
      .filter(x -> x > 0).sorted().parallel().forEach(System.out::println);
    IntStream.of(0, 100).map(x -> x*2).<warning descr="Redundant 'sequential' call: there's subsequent 'sequential' call which overrides this call">sequential()</warning>
      .filter(x -> x > 0).distinct().sequential().forEach(System.out::println);
    Stream.of(0, 100).map(x -> x*2).<warning descr="Redundant 'sequential' call: there's subsequent 'parallel' call which overrides this call">sequential()</warning>
      .filter(x -> x > 0).limit(10).parallel().forEach(System.out::println);

    IntStream.range(0, 100).unordered().filter(x -> x > 50).<warning descr="Redundant 'unordered' call: there already was an 'unordered' call in the chain">unordered()</warning>.forEach(System.out::println);
    IntStream.range(0, 100).unordered().filter(x -> x > 50).sorted().unordered().forEach(System.out::println);
  }
}
