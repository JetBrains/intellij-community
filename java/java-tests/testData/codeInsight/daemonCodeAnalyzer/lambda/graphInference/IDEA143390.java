class Bar {

  interface Foo {}

  interface Predicate<T> {}

  interface Function<F, T> {}

  static public void bar() {
    filter(null,
           and(compose(w(), x()), compose(w(), compose(z(), x())) ));
  }

  static Function<String, Foo> z() {
    return null;
  }

  static Function<String, String> x() {
    return null;
  }

  private static <X> Predicate<X> w() {
    return null;
  }

  public static <T> Iterable<T> filter(final Iterable<T> u, final Predicate<? super T> p) {
    return null;
  }

  public static <T> Predicate<T> and(Predicate<? super T>... c) {
    return null;
  }

  public static <A, B> Predicate<A> compose(Predicate<B> p, Function<A, ? extends B> f) {
    return null;
  }

  public static <A, B, C>Function<A, C> compose(Function<B, C> g, Function<A, B> f) {
    return null;
  }
}