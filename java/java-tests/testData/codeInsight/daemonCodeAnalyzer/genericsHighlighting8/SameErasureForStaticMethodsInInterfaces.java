
interface Test {
  <error descr="'foo(A)' clashes with 'foo(B)'; both methods have same erasure">static <A, B> void foo(A a)</error> {}
  static <A, B> void foo(B b) {}
}