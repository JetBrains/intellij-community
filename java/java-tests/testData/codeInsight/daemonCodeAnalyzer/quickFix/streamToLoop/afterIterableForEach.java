// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

public class Main {
  public void testCast(Object obj, List<Object> list) {
      for (Number n: ((Iterable<Number>) obj)) {
          list.add(n);
      }
  }

  public static void main(String[] args) {
    List<String> list = Arrays.asList("a", "b");
      for (String s: list) {
          System.out.println(s);
      }
  }

  public static <T extends Collection<?>> T test(T collection) {
      for (Object o: collection) {
          System.out.println(o);
      }
      return collection;
  }

  void testRawTypeSupport(List<List> list) {
      for (List l: list) {
          System.out.println(l.size());
      }
  }

  public interface SomeInterface {

    Set<? extends SomeInterface> nodes();

    default void accept(Visitor visitor) {
        for (SomeInterface child: new LinkedHashSet<>(this.nodes())) {
            child.accept(visitor);
        }
    }

    interface Visitor { }
  }
}