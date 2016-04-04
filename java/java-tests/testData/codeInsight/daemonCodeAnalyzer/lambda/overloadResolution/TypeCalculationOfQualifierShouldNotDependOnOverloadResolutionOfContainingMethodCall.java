
import java.util.*;
import java.util.stream.*;

class Main {

  private void <warning descr="Private method 'createFunction(java.util.stream.Stream<java.util.Set<java.lang.String>>)' is never used">createFunction</warning>(Stream<Set<String>> setStream) {
    foo (setStream.flatMap(Collection::stream).collect(Collectors.toList()));
  }

  private void foo(Collection<String> keys) {
    System.out.println(keys);
  }
  private void <warning descr="Private method 'foo(java.util.Set<java.lang.String>)' is never used">foo</warning>(Set<String> keys) {
    System.out.println(keys);
  }


  private void <warning descr="Private method 'legs(java.lang.String...)' is never used">legs</warning>(String... s) {
    System.out.println(s);
  }

  private void legs(List<String> s) {
    System.out.println(s);
  }

  void m(Stream<String> stream) {
    legs(stream.map(l -> l).collect(toImmutableList()));
  }

  static <T> Collector<T, ?, List<T>> toImmutableList() {
    return null;
  }
}
