class Sample {
  interface G<A> {}
  interface G1 extends G {}

  void foo(G1 g1) {
    bar(g1);
  }
  <B>void bar(G<B> gb) {}

  void foo(G1[] g1) {
    bar(g1);
  }
  <B>void bar(G<B>[] gb) {}
}
