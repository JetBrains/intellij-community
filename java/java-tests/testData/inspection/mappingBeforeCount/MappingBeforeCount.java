import java.util.List;
import java.util.stream.IntStream;

public class MappingBeforeCount {
  void test(List<String> list) {
    long count = list.stream().filter(s -> !s.isEmpty()).<warning descr="The 'map()' call does not change the final count and might be optimized out.">map</warning>(String::trim).count();
    long count2 = IntStream.range(0, 100).filter(x -> x % 2 == 1).<warning descr="The 'asLongStream()' call does not change the final count and might be optimized out.">asLongStream</warning>().count();
    long count3 = IntStream.range(0, 100).filter(x -> x % 2 == 1).<warning descr="The 'asDoubleStream()' call does not change the final count and might be optimized out.">asDoubleStream</warning>().count();
    long count31 = IntStream.range(0, 100).asDoubleStream().filter(x -> x % 2 == 1).count();
    long count4 = IntStream.range(0, 100).filter(x -> x % 2 == 1).<warning descr="The 'boxed()' call does not change the final count and might be optimized out.">boxed</warning>().count();
    long count5 = IntStream.range(0, 100).filter(x -> x % 2 == 1).<warning descr="The 'mapToDouble()' call does not change the final count and might be optimized out.">mapToDouble</warning>(x -> x / 2.0).count();
  }
}