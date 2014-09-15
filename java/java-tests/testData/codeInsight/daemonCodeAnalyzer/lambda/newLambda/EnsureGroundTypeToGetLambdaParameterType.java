import java.util.Optional;
import java.util.function.Function;

class CyclicInferenceTest2 {
  void test(final Extractor2<String, ?> pE, final Match2<String, Integer> pMatch) {
    Match2<String, Integer> matcher = pMatch.or(pE, i -> 2);            // "i" is red-highlighted
  }

}
class Match2<T, V> {
  public <W> Match2<T, V> or(Extractor2<T, W> e, Function<W, V> c) {
    return this;
  }
}

interface Extractor2<T, W> {
  Optional<W> unapply(T t);
}