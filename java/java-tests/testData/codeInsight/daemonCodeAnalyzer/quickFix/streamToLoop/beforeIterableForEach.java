// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

public class Main {
  public void testCast(Object obj, List<Object> list) {
    ((Iterable<Number>)obj).forE<caret>ach(n -> list.add(n));
  }

  public static void main(String[] args) {
    List<String> list = Arrays.asList("a", "b");
    list.forEach(System.out::println);
  }

  public static <T extends Collection<?>> T test(T collection) {
    collection.forEach(System.out::println);
    return collection;
  }

  void testRawTypeSupport(List<List> list) {
    list.forEach(l -> System.out.println(l.size()));
  }

  public interface SomeInterface {

    Set<? extends SomeInterface> nodes();

    default void accept(Visitor visitor) {
      new LinkedHashSet<>(this.nodes()).forEach(child -> child.accept(visitor));
    }

    interface Visitor { }
  }
}