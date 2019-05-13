interface A<<warning descr="Type parameter 'K' is never used">K</warning>> {}

abstract class B implements A<B> {}

abstract class C<V extends A<V>> {
  void m(V v){
    C<B> c = <warning descr="Unchecked cast: 'C<V>' to 'C<B>'">(C<B>) this</warning>;
    System.out.println(c);
    System.out.println(v);
  }
}
