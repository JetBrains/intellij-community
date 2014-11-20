class X<T> { }

class A<T, S extends X<T>>  {}

class C {
  void foo(A<?, X<?>> a){ <error descr="Inferred type 'X<?>' for type parameter 'S' is not within its bound; should extend 'X<capture<?>>'">bar(a)</error>; }
  <T, S extends X<T>> void bar(A<T, S> a){  }
}
