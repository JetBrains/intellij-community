interface Foo1<T, N extends Number> {
  void m(T arg);
  void m(N arg);
}
interface Foo extends Foo1<Integer, Integer> {}