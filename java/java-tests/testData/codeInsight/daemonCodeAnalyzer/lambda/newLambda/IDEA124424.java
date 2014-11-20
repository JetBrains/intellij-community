import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

class Main {
  void bar(final Stream<Object> objectStream) {
    foo(objectStream.map(o -> "str").collect(toList()));
  }

  void foo(Iterable<?> k){}
  void foo(String s){}
}
