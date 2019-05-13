class Bug<A, B extends A> {
  <X, Y extends X> void f1(Bug<X, Y> b) {}

  void f2(Bug<?, ?> b) {
    f1(b);
  }
}

class Bug1<A, B> {
  <X, Y extends X> void f1(Bug1<X, Y> b) {}

  void f2(Bug1<?, ?> b) {
    f1<error descr="'f1(Bug1<X,Y>)' in 'Bug1' cannot be applied to '(Bug1<capture<?>,capture<?>>)'">(b)</error>;
  }
}