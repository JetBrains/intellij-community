public class Sample {
  interface G<A> {}
  interface G1 extends G {}
  void foo(G1 g1) {
    bar(g1);
  }
  <B> B bar(G<B> gb) {return null;}

  void f(G1 g1) {
    G<String> l11 =  bar<error descr="'bar(Sample.G<B>)' in 'Sample' cannot be applied to '(Sample.G1)'">(g1)</error>;
    String l1 = bar<error descr="'bar(Sample.G<B>)' in 'Sample' cannot be applied to '(Sample.G1)'">(g1)</error>;
    Object o = bar(g1);
  }
}
