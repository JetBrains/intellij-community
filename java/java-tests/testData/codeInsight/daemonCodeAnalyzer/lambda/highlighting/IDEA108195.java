class Demo {

    Function<Supplier<Double>, ? extends Supplier<Double>> mapper2 = (sa) -> () -> sa.get() + 1.0;

    interface Supplier<T> {
      public T get();
    }
  
    interface Function<T, R> {
      public R apply(T t);
    }
}
