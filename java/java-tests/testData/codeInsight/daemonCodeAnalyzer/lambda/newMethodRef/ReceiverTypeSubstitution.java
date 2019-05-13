import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.stream.Stream;

abstract class Test {
  public void foo(final ConcurrentMap<String, String> pMap) {
    map(pMap::get);
  }

  abstract <R> Stream<R> map(Function<Object, R> mapper);
}