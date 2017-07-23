
import java.util.function.Function;

abstract class View<Tv, Av, Bv> {

  public Const<Av, Tv> apply(final Fixed<Tv, Av, Bv, Const<Av, Tv>, Const<Av, Bv>> fix) {
    return fix.apply(Const::new);
  }
}

@FunctionalInterface
interface Fixed< T, A, B,
  FT extends Functor<T>,
  FB extends Functor<B>> {
  FT apply(Function< A, FB> function);
}

interface Functor<A> {
}

final class Const<A, B> implements Functor<B> {
  A a;

  public Const(A a) {
    this.a = a;
  }
}

