
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;


abstract class Test {

  {
    Consumer<? extends Double> bar = bar(instanceOf(Integer.class), i -> i + 2);
    Consumer<? extends String> bar1 = bar(instanceOf(Integer.class), i -> i + 2);
  }


  abstract  <T, R> Consumer<T>      bar(Predicate<? super T> predicate, Function<T, R> f);
  abstract  <P> Predicate<P> instanceOf(Class<? extends P> type);
}
