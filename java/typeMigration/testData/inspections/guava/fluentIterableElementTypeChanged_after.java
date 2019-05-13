import com.google.common.base.Function;

import java.util.stream.Stream;

public class FluentTransformer {
  public <U, V> Stream<V> transformGeneric(Stream<U> p1, Function<U, V> p2) {
    return p1.map(p2::apply);
  }
}