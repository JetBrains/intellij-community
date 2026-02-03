abstract class A<S> {
  abstract <T> T foo(T x, T y);

  {
    A<? extends A<? super A<Object>>> a = null;
    A<? extends A<? super A<String>>> b = null;
    <selection>foo(a, b)</selection>;
  }
}