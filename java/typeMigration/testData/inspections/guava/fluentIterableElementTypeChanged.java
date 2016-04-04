import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;

import java.util.List;

public class FluentTransformer {
  public <U, V> Fluen<caret>tIterable<V> transformGeneric(FluentIterable<U> p1, Function<U, V> p2) {
    return p1.transform(p2);
  }
}