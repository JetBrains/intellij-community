// The diamond in the lambda body and the f-bounded 'fbound'/'Comparator.comparing' call inside it are not pertinent
// to the applicability of the enclosing call, so the enclosing inference never sees the constraints of the lambda body.
// It must not pick a fresh variable for 'U' then: that happens before the 'a -> a.getB()' constraint is processed at
// all, and the chosen fresh variable is copied along with the other bounds into the replayed nested session, where it
// contradicts the 'String' lower bound which only shows up there.
import java.util.*;
import java.util.function.*;

public class ComparatorInsideDiamondInsideLambda {
  static class A {
    String getB() { return ""; }
  }

  void computeIfAbsent(Map<Integer, Set<A>> m) {
    m.computeIfAbsent(1, x -> new TreeSet<>(Comparator.comparing(a -> a.getB()))).add(new A());
  }

  interface Cmp<T> {}
  interface Base<E> {}
  static class Impl<E> implements Base<E> {
    Impl(Cmp<? super E> c) {}
  }

  static <T, U extends Comparable<? super U>> Cmp<T> fbound(Function<? super T, ? extends U> f) { return null; }

  void f(Supplier<Base<A>> s) {}

  void insideLambdaArgument() {
    f(() -> new Impl<>(fbound(a -> a.getB())));
  }

  void insideLambdaInAssignment() {
    Supplier<Base<A>> s = () -> new Impl<>(fbound(a -> a.getB()));
  }

  void standalone() {
    Base<A> b = new Impl<>(fbound(a -> a.getB()));
  }

  interface Other {}

  void g(Supplier<Base<Other>> s) {}

  //the lambda parameter type is still inferred from the target type of the enclosing lambda
  void otherElementType() {
    g(() -> new Impl<>(fbound(a -> a.<error descr="Cannot resolve method 'getB' in 'Other'">getB</error>())));
  }
}
