import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

abstract class Play {
  public void main(Stream<String> stream, Stream<String> anotherStream) {
    Stream<String> stringStream = stream.map(o -> foo(i -> "")).flatMap(l -> l);
  }

  abstract <RF > Stream<RF> foo(Function<Integer, ? extends RF> mapper);

  static int foo() {
    return 6;
  }
}



class SimplePlay {
  {
    foo(y -> bar(x ->  "")).substring(0);
  }

  interface Res<R> {
    R apply(String s);
  }

  <T> T foo(Res<T> f) {return null;}
  <K> K bar(Res<K> f) {return null;}
}

class Test19 {
  interface Seq<E> extends Iterable<E> {
    static <E> Seq<E> of(Iterable<? extends E> source) {
      return null;
    }

    <R> Seq<R> map(Function<? super E, ? extends R> mapper);
    <R, V> Seq<R> zip(BiFunction<? super E, ? super V, ? extends R> zipper, Seq<V> other);
  }

  interface S extends Seq<String> {
    static S copyOf(Iterable<String> source) {
      return null;
    }
  }

  interface D extends S {

  }

  void test(Seq<D> dseq) {
    BiFunction<D, List<Integer>, Seq<S>> f =
      (d, nums) -> dseq.map(s -> s.zip((text, num) -> text + num, Seq.of(nums)))
        .map(s -> S.copyOf(s));
  }
}
