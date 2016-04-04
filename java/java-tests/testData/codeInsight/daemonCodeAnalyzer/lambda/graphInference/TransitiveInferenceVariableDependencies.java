import java.util.stream.Collectors;
import java.util.stream.Stream;

class Test {
  private void foo(final Stream<String> stream) {
    stream.collect(Collectors.collectingAndThen(Collectors.toList(), list -> list)).get(0).length();
  }
}
