class B<S, M> {}
abstract class A<T> {
  <K> void baz(B<K, K> a) {}
  abstract B<?, ?> foo();
  void bar(A<?> a) {
    baz<error descr="'baz(B<capture<?>,capture<?>>)' in 'A' cannot be applied to '(B<capture<?>,capture<?>>)'">(a.foo())</error>;
  }
}