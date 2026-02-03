class B<T1,S> {}
abstract class A<T> {
  <K> void baz35(B<K, ? extends K> a) {}
  abstract B<T,? super T> foo35();
  void bar35(A<? super T> a){
    baz35<error descr="'baz35(B<K,? extends K>)' in 'A' cannot be applied to '(B<capture<? super T>,capture<? super capture<? super T>>>)'">(a.foo35())</error>;
  }


  <K> void baz44(B<K, ? extends K> a) {}
  abstract B<? super T,? super T> foo44();
  void bar44(A<? super T> a){
    baz44<error descr="'baz44(B<K,? extends K>)' in 'A' cannot be applied to '(B<capture<? super capture<? super T>>,capture<? super capture<? super T>>>)'">(a.foo44())</error>;
  }
}