class InferenceFailBug {
  interface Func1<T1, R> {
    R apply(T1 v1);
    void other();
  }
  interface F1<T1, R> extends Func1<T1, R> {
    default void other() {}
  }

  <T1, R> Func1<T1, R> func(F1<T1, R> f1) { return f1; }

  interface Future<T> {
    <R> Future<R> map(Func1<T, R> f1);
  }
  private Future<Integer> futureExample(Future<String> future) {
    return future.map(func(s -> s.toUpperCase())).map(func(s -> s.length()));
  }
}