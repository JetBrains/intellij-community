import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

class IDEA126969 {
  void foo(final Stream<List<Integer>> stream) {

    stream.flatMap(List::stream)
      .forEach(i -> System.out.println(i.floatValue()));

    stream.flatMap(Collection::stream)
      .forEach(i -> System.out.println(i.floatValue()));

  }
}
