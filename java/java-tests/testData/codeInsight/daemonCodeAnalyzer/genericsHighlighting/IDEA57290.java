
class A<T,S> {
  class B{}
}

class C {
  void foo(A<?,?>.B x){
    bar<error descr="'bar(A<capture<?>,capture<?>>.B)' in 'C' cannot be applied to '(A<capture<?>,capture<?>>.B)'">(x)</error>;
  }
  <T> void bar(A<T,T>.B x){}
}