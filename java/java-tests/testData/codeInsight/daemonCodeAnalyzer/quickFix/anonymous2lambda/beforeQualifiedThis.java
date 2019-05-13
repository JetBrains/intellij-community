// "Replace with lambda" "true"
class Test {
  interface Eff<A, B> {
    B f(A a);
  }

  interface InOut<A> {
    A run() throws IOException;

    default <B> InOut<B> bind(final Eff<A, InOut<B>> f) {
      return new In<caret>Out<B>() {
        @Override
        public B run() throws IOException {
          return f.f(InOut.this.run()).run();
        }
      };
    }
  }
}