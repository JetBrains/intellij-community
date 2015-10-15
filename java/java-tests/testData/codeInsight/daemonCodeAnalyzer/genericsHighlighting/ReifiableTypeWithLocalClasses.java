class B<T> {

  Object[] foo(Object obj) {
    class C {}
    return <error descr="Generic array creation">new C[0]</error>;
  }


  boolean foo1(Object obj) {
    class C {}
    return obj instanceof <error descr="Illegal generic type for instanceof">C</error>;
  }
}