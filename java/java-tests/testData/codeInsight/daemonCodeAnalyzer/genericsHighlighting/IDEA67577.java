class X<T>{}

class A<T,S extends X<T>>  {}

class C {
    void foo(A<?, <error descr="Type parameter 'X' is not within its bound; should extend 'X<?>'">X</error>> a){  }
}