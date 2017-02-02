// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.lang.reflect.Array;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Test {
  Stream<String> names() {
    return Stream.of("foo", "bar");
  }

  public static void main(String[] args) {
    List<String> list = new Test().names().map(String::trim).filter(n -> !n.isEmpty())
      .<caret>collect(Collectors.toList());
  }

  private long counter(Class<? extends Array> list) {
    return stream(list).count();
  }

  public <E> Stream<E> stream(Class<E> clazz) {
    return null;
  }
}