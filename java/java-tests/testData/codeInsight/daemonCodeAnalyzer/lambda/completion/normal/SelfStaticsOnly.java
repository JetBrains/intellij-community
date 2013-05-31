interface Function<T, R> {
  public R apply(T t);

  static <K> Function<K, K> baz() {
    return k -> k;
  }
}

interface IFunction extends Function<Integer, Integer> {
  static void bar() {}
  static void ba() {
    ba<caret>
  }
}