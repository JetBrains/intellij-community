class B<T1,S> {}
abstract class A<T> {
  <K> void baz5(B<K, K> a) {}
  abstract B<T, ? super T> foo5();
  void bar5(A<? super T> a) {
    baz5<error descr="'baz5(B<capture<? super capture<? super T>>,capture<? super capture<? super T>>>)' in 'A' cannot be applied to '(B<capture<? super T>,capture<? super capture<? super T>>>)'">(a.foo5())</error>;
  }

  <K> void baz7(B<K, K> a) {}
  abstract B<T, ? extends T> foo7();
  void bar7(A<?> a) {
    baz7<error descr="'baz7(B<capture<? extends capture<?>>,capture<? extends capture<?>>>)' in 'A' cannot be applied to '(B<capture<?>,capture<? extends capture<?>>>)'">(a.foo7())</error>;
  }

  <K> void baz9(B<K, K> a) {}
  abstract B<T, ? extends T> foo9();
  void bar9(A<? extends T> a) {
    baz9<error descr="'baz9(B<capture<? extends capture<? extends T>>,capture<? extends capture<? extends T>>>)' in 'A' cannot be applied to '(B<capture<? extends T>,capture<? extends capture<? extends T>>>)'">(a.foo9())</error>;
  }


  <K> void baz14(B<K, K> a) {}
  abstract B<? super T, ? super T> foo14();
  void bar14(A<? super T> a) {
    baz14<error descr="'baz14(B<capture<? super capture<? super T>>,capture<? super capture<? super T>>>)' in 'A' cannot be applied to '(B<capture<? super capture<? super T>>,capture<? super capture<? super T>>>)'">(a.foo14())</error>;
  }


  <K> void baz24(B<K, K> a) {}
  abstract B<? extends T, ? extends T> foo24();
  void bar24(A<? extends T> a) {
    baz24<error descr="'baz24(B<capture<? extends capture<? extends T>>,capture<? extends capture<? extends T>>>)' in 'A' cannot be applied to '(B<capture<? extends capture<? extends T>>,capture<? extends capture<? extends T>>>)'">(a.foo24())</error>;
  }
}