// "Replace with lambda" "true-preview"
class Test {
  interface Eff<A, B> {
    B f(A a);
  }

  interface InOut<A> {
    A run() throws IOException;

    default <B> InOut<B> bind(final Eff<A, InOut<B>> f) {
      return () -> f.f(InOut.this.run()).run();
    }
  }
}