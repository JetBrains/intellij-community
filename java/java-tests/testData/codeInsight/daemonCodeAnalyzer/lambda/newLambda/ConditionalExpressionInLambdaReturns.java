
import java.util.function.Function;

interface Container<T> {
  static <Q> Function<Q, Container<Q>> typeTester(Container<Q> c, final Container<Q> one) {
    return funcWithString(input -> c.match(aa -> (true ? (zero()) : one)));
  }

  static <S> Function<S, Container<S>> funcWithString(Function<S, Container<S>> f) {
    return null;
  }

  default <R> R match(Function<T, R> f) {
    return null;
  }

  static <T> Container<T> zero() {
    return null;
  }
}