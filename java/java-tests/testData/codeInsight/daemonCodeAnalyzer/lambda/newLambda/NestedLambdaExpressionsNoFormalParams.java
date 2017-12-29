import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

abstract class NoFormalParams {
  interface I<T> {
    T a(int a);
  }

  <F> I<F> foo(I<F> i) { return null;}

  {
    I<Integer> i =  foo(a -> foo(b -> 1)).a(0);
    foo(a -> foo(b -> 1)).a(0);
  }
}

abstract class NoFormalParamTypeInferenceNeeded {
  interface I<T> {
    T a(int a);
  }

  abstract <RR> RR  map(I<RR> mapper);
  abstract <R, V> R zip(Function<V, R> zipper);

  {
    map(a -> zip(text ->  text));
    zip(a -> zip(text ->  text));
    Integer zip = zip(<error descr="no instance(s) of type variable(s)  exist so that Object conforms to Integer">a -> zip(text -> text)</error>);
  }

}

class IDEA124983 {
  class Rectangle{
    public Rectangle(int i, int j, int h, int w) {
    }
  }

  void createGrid() {
    IntStream.range(0, 4)
      .mapToObj(i -> IntStream.range(0, 4).mapToObj(j -> {
        Rectangle rect2 = new Rectangle(i * 64, j * 64, 64, 64);
        return rect2;
      }))
      .flatMap(s -> s)
      .forEach(this::add);
  }

  void add(final Rectangle r) {}

  void simplified(final IntStream range) {
    range.mapToObj(i -> range.mapToObj(j -> 1)).flatMap(s -> s);
  }
}
