
import java.util.Collection;

interface NumberCollection<N extends Number> extends Collection<N> {
}

interface IntegerCollection<I extends Integer> extends NumberCollection<I> {}
interface IntegerCollection1<I extends Integer, L extends I> extends NumberCollection<L> {}

class Test {
  <T extends Number, C extends NumberCollection<? extends T>> Collection<T> filter(Collection<C> input) {
    return null;
  }

  public void foo(Collection<IntegerCollection<?>> input) {
    Collection<Integer> filtered = filter(input);
  }

  public void foo1(Collection<IntegerCollection1<?, ?>> input) {
    Collection<Integer> filtered = filter(input);
  }
}

