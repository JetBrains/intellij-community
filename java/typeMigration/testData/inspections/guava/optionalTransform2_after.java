import com.google.common.base.Function;

import java.util.Optional;

class Transformer {
  public Optional<String> transform(Optional<Integer> p1, Function<Integer, String> p2) {
    return p1.map(p2::apply);
  }
}