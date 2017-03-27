import java.util.function.BiFunction;
import java.util.function.Function;

interface DuallyParametric<A0, B0> {
  <R> R match(Function<? super A0, ? extends R> aFn, Function<? super B0, ? extends R> bFn);
  static <Aa, Ba> DuallyParametric<Aa, Ba> a(Aa a) {
    return null;
  }

  <Ab, Bb> DuallyParametric<Ab, Bb> b(Bb b);

  <Al, Bl> Bl foldLeft(BiFunction<? super Bl, ? super Al, ? extends Bl> fn, Bl b, Iterable<Al> as);

  default <A, B> DuallyParametric<A, B> merge(DuallyParametric<A, B> first,
                                             BiFunction<? super A, ? super A, ? extends A> aFn,
                                             BiFunction<? super B, ? super B, ? extends B> bFn,
                                             Iterable<DuallyParametric<A, B>> others) {
    return foldLeft((x, y) -> x.match(a1 -> y.match(a2 -> a(aFn.apply(a1, a2)), b -> a(a1)),
                                      b1 -> y.match(DuallyParametric::a, b2 -> b(bFn.apply(b1, b2)))),
                    first,
                    others);
  }
}

interface DuallyParametric1<A> {
  default <R> R match(Function<A, R> aFn) {
    return null;
  }

  static <Ba> DuallyParametric1<Ba> a() {
    return null;
  }

  <Bl> void foldLeft(Function<Bl, Bl> fn, Bl b);

  default void merge() {
    foldLeft((x) -> x.match(a1 -> match(a2 -> a())), this);
  }
}