interface I<T>{}
interface A extends I<I<? super String>> {}
interface B extends I<I<? super Integer>> {}
abstract class X {

  abstract <T> T foo(T x, T y);

  void bar(A x, B y){
      I<? extends I<?>> m = foo(x, y);
  }
}