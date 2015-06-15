
class C56 {

  class A<T,S extends T> {}

  class C {
    void foo(A<?,?> x){
      bar(x);
    }
    <T,S extends T> void bar(A<T,S> x){}
  }
}

class C57 {
  class B<T,S> {}
  class A<T,S extends T> extends B<T,S> {}

  class C {
    void foo(A<?,?> x){
      bar(x);
    }
    <T,S extends T> void bar(B<T,S> x){}
  }
}
