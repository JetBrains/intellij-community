public class Sample {
  interface G<A> {}
  interface G1 extends G {}
  void foo(G1 g1) {
    bar(g1);
  }
  <B> B bar(G<B> gb) {return null;}

  void f(G1 g1) {
    G<String> l11 =  <error descr="Incompatible types. Required G<String> but 'bar' was inferred to B:
Incompatible types: Object is not convertible to G<String>">bar(g1);</error>
    String l1 = <error descr="Incompatible types. Required String but 'bar' was inferred to B:
Incompatible types: Object is not convertible to String">bar(g1);</error>
    Object o = bar(g1);
  }
}
