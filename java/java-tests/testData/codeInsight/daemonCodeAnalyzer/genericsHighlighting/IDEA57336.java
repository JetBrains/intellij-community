
abstract class A<<warning descr="Type parameter 'S' is never used">S</warning>> {
  abstract <T> void foo(A<? extends A<T>> x);
  void  bar(A<? extends A> x){
    foo<error descr="'foo(A<? extends A<T>>)' in 'A' cannot be applied to '(A<capture<? extends A>>)'">(x)</error>;
  }
}