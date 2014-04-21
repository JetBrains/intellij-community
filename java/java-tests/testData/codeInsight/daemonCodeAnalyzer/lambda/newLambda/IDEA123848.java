import java.util.*;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

class Test {

  private static void run() {
    List<R> list = Arrays.asList(new R());

    System.out.println(
      list.stream()
        .collect(groupingBy(r -> r.get(String.class), mapping(r -> r.get(String.class),toList())
        ))
    );
  }

  static class R {
    <T> T get(Class<T> clazz) {
      if (clazz == String.class)
        return (T) "string";

      throw new IllegalArgumentException();
    }
  }
}