import java.util.List;
import java.util.Map;
import java.util.function.Function;

interface DummyOuter<T> {
  default <R> void foo(DummyInner<? super T, R> dummyInner) {}
  default <R> void fooUnbounded(Unbounded<? super T, R> unbounded) {}

  interface DummyInner<T, R> {}
  interface Unbounded<T extends Number, R> {}

  static <E, K, V> DummyInner<? extends E, Map.Entry<K, List<? extends V>>> bar(Function<? super E, ? extends K> f0, Function<? super E, ? extends V> f1) {
    return null;
  }
  static <E> DummyInner<? extends E, String> noArgs() { return null; }
  static <E> Unbounded<?, E> unbounded() { return null; }
  static <E> DummyInner<? super E, String> superBound() { return null; }
  static <E, K, V> DummyInner<? super E, Map.Entry<K, List<? extends V>>> barSuper(Function<? super E, ? extends K> f0, Function<? super E, ? extends V> f1) {
    return null;
  }

  default void main(DummyOuter<Integer> o) {
    o.foo<error descr="'foo(DummyOuter.DummyInner<? super java.lang.Integer,R>)' in 'DummyOuter' cannot be applied to '(DummyOuter.DummyInner<capture<? extends java.lang.Integer>,java.util.Map.Entry<java.lang.Byte,java.util.List<? extends java.lang.Short>>>)'">(DummyOuter.bar(Integer::byteValue, Integer::shortValue))</error>;
    o.foo<error descr="'foo(DummyOuter.DummyInner<? super java.lang.Integer,R>)' in 'DummyOuter' cannot be applied to '(DummyOuter.DummyInner<capture<? extends java.lang.Integer>,java.util.Map.Entry<java.lang.Byte,java.util.List<? extends java.lang.Short>>>)'">(DummyOuter.bar((Integer i) -> i.byteValue(), Integer::shortValue))</error>;
    o.foo<error descr="'foo(DummyOuter.DummyInner<? super java.lang.Integer,R>)' in 'DummyOuter' cannot be applied to '(DummyOuter.DummyInner<capture<?>,java.lang.String>)'">(noArgs())</error>;
    o.fooUnbounded<error descr="'fooUnbounded(DummyOuter.Unbounded<? super java.lang.Integer,R>)' in 'DummyOuter' cannot be applied to '(DummyOuter.Unbounded<capture<?>,java.lang.Object>)'">(unbounded())</error>;
    o.foo(superBound());
  }

  default void superCapture() {
    var o = new DummyOuter<Map.Entry<Integer, String>>() {};
    o.foo(DummyOuter.barSuper(Map.Entry::getKey, Map.Entry::getValue));
  }
}
