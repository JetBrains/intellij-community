// "Fix all 'Stream API call chain can be simplified' problems in file" "true"

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.StreamSupport;

public class Test {
  public static void test(List<String> list, Collection<Number> collection, Iterable<Integer> iterable) {
    StreamSupport.str<caret>eam(list.spliterator(), false).filter(Objects::nonNull).forEach(System.out::println);
    StreamSupport.stream(collection.spliterator(), true).forEach(System.out::println);
    StreamSupport.stream(iterable.spliterator(), true).forEach(System.out::println);
    StreamSupport.stream(list.spliterator(), collection.isEmpty()).forEach(System.out::println);
  }
}
