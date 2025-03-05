public class Sample {
  interface G<A> {}
  interface G1 extends G {}
  void foo(G1 g1) {
    bar(g1);
  }
  <B> B bar(G<B> gb) {return null;}

  void f(G1 g1) {
    G<String> l11 =  <error descr="Incompatible types. Found: 'java.lang.Object', required: 'Sample.G<java.lang.String>'">bar</error>(g1);
    String l1 = <error descr="Incompatible types. Found: 'java.lang.Object', required: 'java.lang.String'">bar</error>(g1);
    Object o = bar(g1);
  }
}
