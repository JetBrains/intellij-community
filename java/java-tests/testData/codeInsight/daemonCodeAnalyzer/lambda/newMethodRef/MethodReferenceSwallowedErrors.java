
import java.util.function.Supplier;
import java.util.function.BiFunction;
import java.util.function.Function;

interface Regression<S, T, A, B> {
  <F extends Functor, FT extends Functor<T, F>, FB extends Functor<B, F>> FT apply(
    Function<? super A, ? extends FB> fn, S s);

  static <S, T, A, B> Regression<S, T, A, B> regression(Function<? super S, ? extends A> getter,
                                                        BiFunction<? super S, ? super B, ? extends T> setter) {
    return new Regression<S, T, A, B>() {
      @Override
      @SuppressWarnings("unchecked")
      public <F extends Functor, FT extends Functor<T, F>, FB extends Functor<B, F>> FT apply(
        Function<? super A, ? extends FB> fn,
        S s) {
        return null; //doesn't matter
      }
    };
  }

  @SuppressWarnings("unchecked")
  static <S, A> Simple<S, A> simple(Function<? super S, ? extends A> getter,
                                    BiFunction<? super S, ? super A, ? extends S> setter) {
    return regression(getter, setter)::<error descr="Incompatible equality constraint: capture of ? super A and B">apply</error>; // java9 fails to compile this line
  }

  interface Functor<X, F extends Functor> {
  }

  @FunctionalInterface
  interface Simple<S, A> extends Regression<S, S, A, A> {
  }
}

interface SimplifiedTest<B> {
  <FB extends Functor<B>> FB apply(Supplier<? extends FB> fn);

  static <A> SimplifiedTest<A> simple(final SimplifiedTest<? super A> regression) {
    return regression::<error descr="no instance(s) of type variable(s)  exist so that capture of ? extends FB conforms to Functor<capture of ? super A>">apply</error>;
  }

  interface Functor<X> { }
}