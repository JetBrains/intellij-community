interface BiFun<T, U, R> {
  R aly(T t, U u);
}

class Test {
  public static <A, B> void foo() {
      BiFun<? super A, ? super B, ? extends Pair<A, B>> p = Pair::new;
      BiFun<? super A, ? super B, ? extends Pair<A, B>> p1 = Pair::create;
  }

  private static class Pair<A, B> {
    private final A a;
    private final B b;

    protected Pair(A a, B b) {
      this.a = a;
      this.b = b;
    }

    static <M, N> Pair<M, N> create (M m, N n) {return null;}
  }
}
