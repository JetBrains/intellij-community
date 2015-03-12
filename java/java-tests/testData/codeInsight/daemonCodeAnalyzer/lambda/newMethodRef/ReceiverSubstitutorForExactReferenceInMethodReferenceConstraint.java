import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class Test {
  private Map<String, PathParam<?>> foo(final Stream<PathParam<?>> pStream) {
    return pStream.collect(Collectors.toMap(PathParam::getName, Function.identity()));
  }
}

class PathParam<V> extends NameValueEntity<String, V> {}

class NameValueEntity<N, V> {
  public N getName() {
    return null;
  }
}