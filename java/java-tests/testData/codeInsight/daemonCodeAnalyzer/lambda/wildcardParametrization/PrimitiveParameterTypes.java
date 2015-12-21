class IDEA100385 {
  void foo(N<Double> n){
    n.forEach(<error descr="Cannot infer functional interface type">(double e) -> { }</error>);
  }
  static interface N<E> {
    void forEach(Consumer<? extends E> consumer);
  }

  interface Consumer<T> {
    public void accept(T t);
  }

}
