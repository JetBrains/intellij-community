
import java.util.function.BiFunction;
import java.util.function.Function;

class Test {
  interface MyBaseStream<T, S extends MyBaseStream<T, S>> {
    <R> MyStream<R> map(Function<? super T, ? extends R> mapper);
  }

  interface MyStream<T> extends MyBaseStream<T, MyStream<T>> {}

  {
    BiFunction<MyStream<Integer>, Function<Integer, Integer>, MyStream<Integer>> streamMapper = MyStream::map;
  }
}