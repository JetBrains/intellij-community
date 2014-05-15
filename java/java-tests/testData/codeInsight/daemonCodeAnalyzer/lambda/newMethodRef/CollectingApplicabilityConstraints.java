import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

class Base {

  interface Seq<E> extends Iterable<E> {
    static <E> Seq<E> of(Iterable<? extends E> source) {
      return null;
    }

    <R> Seq<R> map(Function<? super E, ? extends R> mapper);
  }

}

class Test3 extends Base {

  static void test3(Seq<String[]> many) {
    Seq<Seq<String>> mapped = many.map(Arrays::asList).map(Seq::of);
    Seq<Seq<String>> mappedL = many.map(Arrays::asList).map(list -> Seq.of(list));
  }

  public static <T> List<T> asList(T[] a) {
    return null;
  }
}
