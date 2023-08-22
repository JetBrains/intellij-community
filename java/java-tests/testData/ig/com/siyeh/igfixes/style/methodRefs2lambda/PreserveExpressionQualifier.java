import java.util.function.Function;

abstract class Result<V> {
  public abstract <U> Result<U> map(Function<V, U> f);

  <B, C> void m(final Result<B> b) {
    Function<Function<B, C>, Result<C>> map = b:<caret>:map;
  }

}