class Test {
  public static final BinaryOperator<Integer> rPlus = (x, y) -> x + y;

  interface BinaryOperator<T> extends Combiner<T,T,T> {
    public T operate(T left, T right);

    @Override
    default T combine(T t1, T t2) {
      return operate(t1, t2);
    }
  }

  interface Combiner<T, U, V> {
    V combine(T t, U u);
  }
}
