
import java.util.Optional;
import java.util.function.Function;

import static java.util.Optional.of;

class MatchTest {

  {
    Match<String, Integer> match = match((String s) -> s.equals("1") ? of(1) : null, s -> 1);
  }

  private <M>Optional<M> bar() {
    return null;
  }

  public static <T1, V1, W1> Match<T1, V1> match(Extractor<T1, W1> e, Function<W1, V1> c) {
    return null;
  }

  class Match<T, V> {}

  interface Extractor<T, W> {
    Optional<W> unapply(T t);
  }
}
