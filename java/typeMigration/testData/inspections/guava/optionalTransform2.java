import com.google.common.base.Function;
import com.google.common.base.Optional;

class Transformer {
  public Optio<caret>nal<String> transform(Optional<Integer> p1, Function<Integer, String> p2) {
    return p1.transform(p2);
  }
}