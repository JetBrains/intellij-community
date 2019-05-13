class B<T1,S> {}
abstract class A<T> {

  <K> void baz253(B<? super K, ?> a) {}
  abstract B<? super T, ? super T> foo253();
  void bar253(A<?> a) {
    baz253(a.foo253());
  }


  <K> void baz255(B<? super K, ?> a) {}
  abstract B<? super T, ? super T> foo255();
  void bar255(A<? extends T> a) {
    baz255(a.foo255());
  }


  <K> void baz256(B<? super K, ?> a) {}
  abstract B<? super T, ? extends T> foo256();
  void bar256(A<?> a) {
    baz256(a.foo256());
  }


  <K> void baz258(B<? super K, ?> a) {}
  abstract B<? super T, ? extends T> foo258();
  void bar258(A<? extends T> a) {
    baz258(a.foo258());
  }


  <K> void baz259(B<? super K, ?> a) {}
  abstract B<? super T, ?> foo259();
  void bar259(A<?> a) {
    baz259(a.foo259());
  }

  <K> void baz261(B<? super K, ?> a) {}
  abstract B<? super T, ?> foo261();
  void bar261(A<? extends T> a) {
    baz261(a.foo261());
  }
}