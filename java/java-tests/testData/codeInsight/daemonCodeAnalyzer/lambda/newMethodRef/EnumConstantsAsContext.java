import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.BiFunction;


interface I {
  default Optional<AtomicReference<String>> track(final String key) {
    return null;
  }
}

enum Args {
  track(I::track, AtomicReference::get, "", "");

  <T, U, V> Args(final BiFunction<I, String, Optional<T>> ctor,
                 final Function<T, V> unctor, final String oldValue,
                 final V oldExpected) {
  }
}