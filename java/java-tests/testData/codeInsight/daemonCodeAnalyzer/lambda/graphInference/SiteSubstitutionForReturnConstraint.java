import java.util.function.Function;
import java.util.stream.Collector;

class Collectors {

  public static <A,R,RR> void collectingAndThen(Function<R, RR> finisher, Function<A, R> finisher1) {
    Function<A, RR> f = finisher1.andThen(finisher);
  }

}


class Collectors1 {
  public static<T,A,R,RR> Collector<T,A,RR> collectingAndThen(Function<R, RR> finisher, Function<A, R> function) {
    return factory(function.andThen(finisher));
  }

  static <Ts, As, Rs> Collector<Ts, As, Rs> factory(Function<As, Rs> f) {
    return null;
  }
}