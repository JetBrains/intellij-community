import com.google.common.collect.FluentIterable;

import java.util.List;

public class Main {
  void m(Object input, List<Object> transformed) {
    transformed.addAll(FluentIterable.fro<caret>m((Iterable<Object>) input).toList());
  }
}