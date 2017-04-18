
import java.util.function.Function;


import static java.util.function.Function.identity;

class Example {

  @FunctionalInterface
  public interface Functor<A, F extends Functor> {
    <B> Functor<B, F> fmap(Function<? super A, ? extends B> fn);
  }

  @FunctionalInterface
  public interface NaturalTransformation<A, FA extends Functor<A, ?>, GA extends Functor<A, ?>> extends Function<FA, GA> {
    static <A, FA extends Functor<A, ?>> NaturalTransformation<A, FA, FA> endo(
      NaturalTransformation<A, FA, FA> naturalTransformation) {
      return naturalTransformation;
    }

    static void main(String[] args) {
      NaturalTransformation<Integer, NatF<Integer>, NatF<Integer>> happy =
        natF -> natF.match(identity(), s -> NatF.s(s.carrier() + 1)); //no problems

      // compiler exception reported on the following line
      NaturalTransformation<Integer, NatF<Integer>, NatF<Integer>> unhappy =
        NaturalTransformation.endo(natF -> natF.match(identity(), s -> NatF.s(s.carrier() + 1)));
    }
  }

  public interface Coproduct2<A, B> {
    <R> R match(Function<? super A, ? extends R> aFn, Function<? super B, ? extends R> bFn);
  }

  public static abstract class NatF<A> implements Functor<A, NatF>, Coproduct2<NatF.Z<A>, NatF.S<A>> {

    private NatF() {
    }

    public static <A> NatF<A> z() {
      return new Z<>();
    }

    public static <A> NatF<A> s(A a) {
      return new S<>(a);
    }

    public static final class Z<A> extends NatF<A> {
      private Z() {
      }

      @Override
      @SuppressWarnings("unchecked")
      public <B> Z<B> fmap(Function<? super A, ? extends B> fn) {
        return (Z<B>) this;
      }

      @Override
      public <R> R match(Function<? super Z<A>, ? extends R> aFn,
                         Function<? super S<A>, ? extends R> bFn) {
        return aFn.apply(this);
      }
    }

    public static final class S<A> extends NatF<A> {
      private final A a;

      private S(A a) {
        this.a = a;
      }

      public A carrier() {
        return a;
      }

      @Override
      public <B> S<B> fmap(Function<? super A, ? extends B> fn) {
        return new S<>(fn.apply(carrier()));
      }

      @Override
      public <R> R match(Function<? super Z<A>, ? extends R> aFn,
                         Function<? super S<A>, ? extends R> bFn) {
        return bFn.apply(this);
      }
    }
  }
}

class ExampleSimplified {
  static void main(String[] args) {
    Function<Z<Integer>, Z<Integer>> zzFunction = t -> t;
    Function<NatF<Integer>, NatF<Integer>> unhappy = endo(natF -> natF.match(zzFunction, s -> s1(s.carrier())));
  }

  static <FA> Function<FA, FA> endo(Function<FA, FA> function) {
    return function;
  }

  public static <A1> NatF<A1> s1(A1 a) {
    return null;
  }

  public static abstract class NatF<A> {
    abstract <R> R match(Function<Z<A>, ? extends R> aFn,
                         Function<S<A>, ? extends R> bFn);
  }

  public static abstract class Z<A> extends NatF<A> {}

  public static abstract class S<A> extends NatF<A> {

    public A carrier() {
      return null;
    }
  }
}