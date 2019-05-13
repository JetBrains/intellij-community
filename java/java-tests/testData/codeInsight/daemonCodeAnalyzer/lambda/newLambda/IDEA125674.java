import java.util.stream.Collectors;
import java.util.stream.Stream;

class Test {
  void foo(final Stream<String> stream){
    stream.collect(Collectors.toMap(s -> s, s -> s, (a, b) -> a.length() > b. length() ? a : b));
  }
}
