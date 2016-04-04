import java.util.function.Consumer;

class Test {
  interface IOfInt extends Consumer<Integer> {
    default void accept(Integer i) {}
  }

  interface TS<T> extends Consumer<T> {}
  interface TS1<T> extends TS<T> {}

  class OfInt implements TS<Integer>, IOfInt {}  
  class OfInt1 implements Consumer<Integer>, IOfInt {}  
  class OfInt2 implements TS<Integer>, IOfInt {}  
}
