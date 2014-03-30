class Main {

  <T, R, P> Collector<T, R> m(Supplier<? extends R> supplier, BiConsumer<R, T, P> accumulator) {
    return null;
  }

  Collector<String, Main> test2(Supplier<Main> sb) {
    return m(sb, Main::append);
  }

  public Main append(String... str) {
    return this;
  }


  interface Supplier<T> {
    public T get();
  }

  interface Collector<T, R> {
  }

  interface BiConsumer<T, U, P> {
    void accept(T t);
  }

}

class Main1 {

  <T, R, P> Collector<T, R> m(Supplier<? extends R> supplier, BiConsumer<R, T, P> accumulator) {
    return null;
  }

  Collector<String, Main1> test2(Supplier<Main1> sb) {
    return m(sb, Main1::append);
  }

  public Main1 append(Main1... str) {
    return this;
  }


  interface Supplier<T> {
    public T get();
  }

  interface Collector<T, R> {
  }

  interface BiConsumer<T, U, P> {
    void accept(T t);
  }

}