import java.util.Map;


abstract class Test<Tt> {

  public Map<String, Long> getNumberOfInstancesForEachWord() {
    return collect(groupingBy(counting()));
  }

  abstract <R> R collect(Collector<? super Tt, R> collector);
  abstract <Tg, M> Collector<Tg, M> groupingBy(Collector<? super Tg,  Long> downstream);
  abstract <Tc> Collector<Tc, Long> counting();

  interface Collector<T,  R> {}
}

abstract class Test1<Tt> {

  public Map<String, Long> getNumberOfInstancesForEachWord() {
    return collect(groupingBy(counting()));
  }

  abstract <R> R collect(Collector<Tt, R> collector);
  abstract <Tg, M> Collector<Tg, M> groupingBy(Collector<Tg,  Long> downstream);
  abstract <Tc> Collector<Tc, Long> counting();

  interface Collector<T,  R> {}
}

abstract class Test2<Tt> {

  public Map<String, Long> getNumberOfInstancesForEachWord() {
    return collect(groupingBy(counting()));
  }

  abstract <R> R collect(Collector<Tt, R> collector);
  abstract <Tg, M> Collector<Tg, M> groupingBy(Collector<? extends Tg,  Long> downstream);
  abstract <Tc> Collector<Tc, Long> counting();

  interface Collector<T,  R> {}
}