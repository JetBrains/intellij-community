import java.util.function.Function;

class Sample {

  static void original() {
    // Javac compiles this. The type to search is the capture conversion of P1, so the receiver
    // constraint compares one capture variable with itself.
    Object a = sample(Sampler::sample0);
  }

  static <R, E extends Exception> R sample(Function<? super Sampler<?, E>, ? extends R> function) {
    return function.apply(null);
  }

  interface Sampler<$F extends Sampler<$F, E>, E extends Exception> {

    <T> T sample0();

    // this method shows the role of the E parameter
    <T> T sample1(Run<? extends E> run);
  }

  @FunctionalInterface
  interface Run<E extends Exception> {

    void run() throws E;
  }

  static void withoutSecondParameter() {
    Object a = fBounded(FBounded::sample0);
  }

  interface FBounded<$F extends FBounded<$F>> {
    <T> T sample0();
  }

  static <R> R fBounded(Function<? super FBounded<?>, ? extends R> function) {
    return function.apply(null);
  }

  static void withoutFBound() {
    Object a = plain(Plain::sample0);
  }

  interface Plain<F, E extends Exception> {
    <T> T sample0();
  }

  static <R, E extends Exception> R plain(Function<? super Plain<?, E>, ? extends R> function) {
    return function.apply(null);
  }

  static void withoutSuperWildcard() {
    Object a = exact(FBounded::sample0);
  }

  static <R> R exact(Function<FBounded<?>, ? extends R> function) {
    return function.apply(null);
  }
}
