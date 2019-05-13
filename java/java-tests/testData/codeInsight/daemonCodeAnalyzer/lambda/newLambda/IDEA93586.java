interface Eff<A, B> {
  B f(A a);
}

class Disfunction {
  public static <A, B, C> Eff<C, B> apply(final Eff<C, Eff<A, B>> cab, final Eff<C, A> ca) {
    return bind(cab, f -> compose(a -> f.f(a), ca));
  }

  public static <A, B, C> Eff<C, B> bind(final Eff<C, A> ma, final Eff<A, Eff<C, B>> f) {
    return m -> f.f(ma.f(m)).f(m);
  }

  public static <A, B, C> Eff<A, C> compose(final Eff<B, C> f, final Eff<A, B> g) {
    return a -> f.f(g.f(a));
  }
}
