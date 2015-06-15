class B<T1,S> {}
abstract class A<T> {

  <K> void baz253(B<? super K, ?> a) {}
  abstract B<? super T, ? super T> foo253();
  void bar253(A<?> a) {
    baz253<error descr="'baz253(B<? super java.lang.Object,?>)' in 'A' cannot be applied to '(B<capture<? super capture<?>>,capture<? super capture<?>>>)'">(a.foo253())</error>;
  }


  <K> void baz255(B<? super K, ?> a) {}
  abstract B<? super T, ? super T> foo255();
  void bar255(A<? extends T> a) {
    baz255<error descr="'baz255(B<? super java.lang.Object,?>)' in 'A' cannot be applied to '(B<capture<? super capture<? extends T>>,capture<? super capture<? extends T>>>)'">(a.foo255())</error>;
  }


  <K> void baz256(B<? super K, ?> a) {}
  abstract B<? super T, ? extends T> foo256();
  void bar256(A<?> a) {
    baz256<error descr="'baz256(B<? super java.lang.Object,?>)' in 'A' cannot be applied to '(B<capture<? super capture<?>>,capture<? extends capture<?>>>)'">(a.foo256())</error>;
  }


  <K> void baz258(B<? super K, ?> a) {}
  abstract B<? super T, ? extends T> foo258();
  void bar258(A<? extends T> a) {
    baz258<error descr="'baz258(B<? super java.lang.Object,?>)' in 'A' cannot be applied to '(B<capture<? super capture<? extends T>>,capture<? extends T>>)'">(a.foo258())</error>;
  }


  <K> void baz259(B<? super K, ?> a) {}
  abstract B<? super T, ?> foo259();
  void bar259(A<?> a) {
    baz259<error descr="'baz259(B<? super java.lang.Object,?>)' in 'A' cannot be applied to '(B<capture<? super capture<?>>,capture<?>>)'">(a.foo259())</error>;
  }

  <K> void baz261(B<? super K, ?> a) {}
  abstract B<? super T, ?> foo261();
  void bar261(A<? extends T> a) {
    baz261<error descr="'baz261(B<? super java.lang.Object,?>)' in 'A' cannot be applied to '(B<capture<? super capture<? extends T>>,capture<?>>)'">(a.foo261())</error>;
  }
}