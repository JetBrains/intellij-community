class A<T,S> {}

class C {
  void foo(A<?,?> x){
    <error descr="Inferred type 'capture<?>' for type parameter 'S' is not within its bound; should extend 'capture<?>'">bar(x)</error>;
  }
  <T,S extends T> void bar(A<T,S> x){}
}