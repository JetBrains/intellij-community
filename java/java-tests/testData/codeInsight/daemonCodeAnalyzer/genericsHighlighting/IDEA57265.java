class A<T> {
  class B<S> {
    <error descr="Improperly formed type: 'B' needs type arguments because its qualifier has type arguments">A<T>.B</error> x;
  }
}