import java.util.Collection;
import java.util.function.Function;
import java.util.stream.Stream;

class Reproducer {

  // Case 1: T's bound is self-contained
  abstract static class SingleParam<T extends SingleParam<T>> {}

  static SingleParam<?> factory1a(String s) { return null; }
  static SingleParam<?> factory1b(String s) { return null; }
  static SingleParam<?> factory1c(String s) { return null; }

  static Stream<Function<String, SingleParam<?>>> works() {
    return Stream.of(Reproducer::factory1a, Reproducer::factory1b, Reproducer::factory1c);
  }

  // Case 2: T's bound references a second type param
  abstract static class TwoParam<T extends TwoParam<T, U>, U> {}

  static <U> TwoParam<?, U> factory2a(U u) { return null; }
  static <U> TwoParam<?, U> factory2b(U u) { return null; }
  static <U> TwoParam<?, U> factory2c(U u) { return null; }

  static Stream<Function<String, TwoParam<?, String>>> twoParam() {
    return Stream.of(Reproducer::factory2a, Reproducer::factory2b, Reproducer::factory2c);
  }

  // Case 3: four interacting type params
  abstract static class FourParam<T extends FourParam<T, A, E, X>, A extends Collection<? extends E>, E, X> {}

  static <E> FourParam<?, Collection<? extends E>, E, Object> factory3a(Collection<? extends E> c) { return null; }
  static <E> FourParam<?, Collection<? extends E>, E, Object> factory3b(Collection<? extends E> c) { return null; }
  static <E> FourParam<?, Collection<? extends E>, E, Object> factory3c(Collection<? extends E> c) { return null; }

  static Stream<Function<Collection<String>, FourParam<?, Collection<? extends String>, String, Object>>> fourParam() {
    return Stream.of(Reproducer::factory3a, Reproducer::factory3b, Reproducer::factory3c);
  }
}
