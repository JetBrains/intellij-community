class A<T> {
  class B<S> {
    <error descr="Improper formed type; some type parameters are missing">A<T>.B</error> x;
  }
}
