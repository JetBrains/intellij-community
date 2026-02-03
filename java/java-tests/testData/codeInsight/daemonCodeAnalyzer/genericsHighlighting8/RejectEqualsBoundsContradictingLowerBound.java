class B<T1,S> {}
abstract class A<T> {

  <K> void baz37(B<K, ? extends K> a) {}
  abstract B<T,? extends T> foo37();
  void bar37(A<?> a){
    baz37(a.foo37());
  }

  <K> void baz39(B<K, ? extends K> a) {}
  abstract B<T,? extends T> foo39();
  void bar39(A<? extends T> a){
    baz39(a.foo39());
  }

  <K> void baz52(B<K, ? extends K> a) {}
  abstract B<? extends T,? extends T> foo52();
  void bar52(A<?> a){
    baz52<error descr="'baz52(B<K,? extends K>)' in 'A' cannot be applied to '(B<capture<? extends capture<?>>,capture<? extends capture<?>>>)'">(a.foo52())</error>;
  }

  <K> void baz54(B<K, ? extends K> a) {}
  abstract B<? extends T,? extends T> foo54();
  void bar54(A<? extends T> a){
    baz54<error descr="'baz54(B<K,? extends K>)' in 'A' cannot be applied to '(B<capture<? extends capture<? extends T>>,capture<? extends capture<? extends T>>>)'">(a.foo54())</error>;
  }


  <K> void baz58(B<K, ? extends K> a) {}
  abstract B<?,?> foo58();
  void bar58(A<?> a){
    baz58<error descr="'baz58(B<K,? extends K>)' in 'A' cannot be applied to '(B<capture<?>,capture<?>>)'">(a.foo58())</error>;
  }


  <K> void baz59(B<K, ? extends K> a) {}
  abstract B<?,?> foo59();
  void bar59(A<? super T> a){
    baz59<error descr="'baz59(B<K,? extends K>)' in 'A' cannot be applied to '(B<capture<?>,capture<?>>)'">(a.foo59())</error>;
  }


  <K> void baz60(B<K, ? extends K> a) {}
  abstract B<?,?> foo60();
  void bar60(A<? extends T> a){
    baz60<error descr="'baz60(B<K,? extends K>)' in 'A' cannot be applied to '(B<capture<?>,capture<?>>)'">(a.foo60())</error>;
  }
}