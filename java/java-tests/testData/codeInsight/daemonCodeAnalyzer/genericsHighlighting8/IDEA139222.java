class Test {
  void bar() {
    baz<error descr="'baz(S)' in 'Test' cannot be applied to '(java.io.Serializable & java.lang.Comparable<? extends java.io.Serializable & java.lang.Comparable<?>>)'">(foo(1, ""))</error>;
  }

  <T> T foo(T x, T y) {
    return x;
  }

  <S extends Comparable<S>> void baz(S x) { }
}