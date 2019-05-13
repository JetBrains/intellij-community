import java.util.List;
import java.util.function.Function;
class Base {

  interface Seq<Eq> extends Iterable<Eq> {
    static <E> Seq<E> of(Iterable<? extends E> source) {
      return null;
    }

    <R> Seq<R> map(Function<? super Eq, ? extends R> mapper);
  }

}

class Test3 extends Base {

  void test4(Seq<List<String>> map) {
    Seq<Seq<String>> mapped = map.map(Seq::of);
  }
}