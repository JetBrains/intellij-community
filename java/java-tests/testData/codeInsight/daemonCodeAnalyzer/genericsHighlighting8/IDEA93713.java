class Bug<A, B extends A> {
  <X, Y extends X> void f1(Bug<X, Y> b) {}

  void f2(Bug<?, ?> b) {
    f1(b);
  }
}

class Bug1<A, B> {
  <X, Y extends X> void f1(Bug1<X, Y> b) {}

  void f2(Bug1<?, ?> b) {
    <error descr="Inferred type 'capture<?>' for type parameter 'Y' is not within its bound; should extend 'capture<?>'">f1(b)</error>;
  }
}