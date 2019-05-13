
import java.util.function.Supplier;

interface DuallyParametric<A0> {
  default <R> R match(Supplier<R> bFn) {
    return null;
  }

  static <Ab> DuallyParametric<Ab> b() {
    return null;
  }

  static void foldLeft(Runnable fn) {}

  default void merge(DuallyParametric<A0> first) {
    foldLeft(() -> {
      Supplier<DuallyParametric<A0>> bDuallyParametricFunction = () -> first.match(() -> b());
    });
  }
}