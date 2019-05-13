class Tmp
{
  interface BiFunction<T, U, R> {
    R apply(T t, U u);
  }

  interface Sequence<T>
  {
    <R> Sequence<R> scan(R init, BiFunction<R, T, R> func);
  }

  static <T> void foo(Sequence<T> sequence){}

  void test(Sequence<String> strings) {
    foo(strings.scan(1, (i, s) -> 1));
  }
}