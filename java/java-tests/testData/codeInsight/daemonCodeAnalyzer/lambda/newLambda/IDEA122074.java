class CyclicInferenceBug {
  interface Func1<T1, R> {
    R apply(T1 v1);
    void other();
  }
  interface F1<T1, R> extends Func1<T1, R> {
    default void other() {}
  }

  <T1, R> Func1<T1, R> func(F1<T1, R> f1) { return f1; }

  void test() {
    Func1<String, String> f1 = func(s -> s);
  }
}