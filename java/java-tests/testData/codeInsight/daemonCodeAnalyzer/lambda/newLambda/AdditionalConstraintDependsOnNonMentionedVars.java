import java.util.function.Function;

abstract class Test {
  abstract <T, A, R> Collector<T, A, R> create(Foo<A> foo, Function<A,R> fun);
  abstract <Ts> Foo<Ts[]> toArray(Ts identity);

  <Tf> Collector<Tf, ?, Tf> foo(Tf t) {
    return create(toArray(t), a -> a[0]);
  }

  interface Collector<T1, A1, R1> {}
  class Foo<D> {}
}
