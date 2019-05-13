class Sample {
  interface G<A> {}
  interface G1 extends G {}

  <B> B bar(B b) {return null;}

  void f(G1 g1) {
    G<String> l11 = bar(g1);
  }
}
