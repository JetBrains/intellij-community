import com.google.common.base.Function;

class Transformer {
  public java.util.Optional<String> transform(java.util.Optional<Integer> p1, Function<Integer, String> p2) {
    return p1.map(p2::apply);
  }
}