// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Test {
  Stream<String> names() {
    return Stream.of("foo", "bar");
  }

  public static void main(String[] args) {
      List<String> list = new ArrayList<>();
      for (Iterator<String> it = new Test().names().iterator(); it.hasNext(); ) {
          String name = it.next();
          String n = name.trim();
          if (!n.isEmpty()) {
              list.add(n);
          }
      }
  }

  private long counter(Class<? extends Array> list) {
      long count = 0L;
      for (Iterator<? extends Array> it = stream(list).iterator(); it.hasNext(); ) {
          Array array = it.next();
          count++;
      }
      return count;
  }

  public <E> Stream<E> stream(Class<E> clazz) {
    return null;
  }
}