
import java.util.stream.IntStream;

class Test {
  private void foo(final IntStream range) {
    range.mapToObj(i -> range.mapToObj(j -> 1))
      .flatMap(s -> s);
  }
}