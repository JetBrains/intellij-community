abstract class Foo<T> {
  void main() {
    listOf(1, 2, 3).filter(it -> it % 2 == 0)<# Foo<Integer> #>
      .map(it -> it * 2)<# Foo<int> #>
      .map(it -> "item: " + it)<# Foo<Object> #>
      .forEach(this::println);
  }

  abstract Void println(Object any);
  abstract Foo<Integer> listOf(int... args);
  abstract Foo<T> filter(Function<T, Boolean> isAccepted);
  abstract <R> Foo<R> map(Function<T, R> mapper);
  abstract void forEach(Function<T, Void> fun);
  interface Function<T, R> {
    R call(T t);
  }
}