 class X<T> {
  class XX extends X<X> {}
  void <T> f(X<T> x, T t) {
      f(new XX(), new X());
  }
}