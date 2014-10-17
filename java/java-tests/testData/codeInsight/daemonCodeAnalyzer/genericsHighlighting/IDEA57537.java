class B<T>{
  class C {}
}
class D<T> extends B<B<T>>{
  void foo(D<?>.C x){
    bar<error descr="'bar(B<B<S>>.C)' in 'D' cannot be applied to '(D<?>.C)'">(x)</error>;
  }

  <S> void bar(D<S>.C x){}
}