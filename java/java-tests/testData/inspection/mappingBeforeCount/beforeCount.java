// "Fix all 'Mapping call before count()' problems in file" "true"
import java.util.List;
import java.util.stream.IntStream;

public class MappingBeforeCount {
  void test(List<String> list) {
    long count = list.stream().filter(s -> !s.isEmpty()).map(String::trim).count();
    long count2 = IntStream.range(0, 100).filter(x -> x % 2 == 1).asLongStream().count();
    long count3 = IntStream.range(0, 100).filter(x -> x % 2 == 1).asDoubleStream().count();
    long count31 = IntStream.range(0, 100).asDoubleStream().filter(x -> x % 2 == 1).count();
    long count4 = IntStream.range(0, 100).filter(x -> x % 2 == 1).boxed().count();
    long count5 = IntStream.range(0, 100).filter(x -> x % 2 == 1).mapToDou<caret>ble(x -> x / 2.0).count();
  }
}