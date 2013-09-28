abstract class F<A, B> {
  public abstract B f(A a);

  public final F<A, P1<B>> lazy() {
    return new F<A, P1<B>>() {
      public P1<B> f(final A a) {
        return null;
      }
    };
  }

  private class TestClient<A, B> extends F<A, P1<B>> {
    public P1<B> f(final A a) {
      return null;
    }
  }
}

class P1<T> {
}
