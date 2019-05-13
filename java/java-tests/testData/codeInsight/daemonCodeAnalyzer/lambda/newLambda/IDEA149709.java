
import java.util.Collection;
import java.util.Set;
import java.util.function.Function;

class Test {
  {
    Set<Class<? extends Throwable>> exceptions = null;
    transform(exceptions, x -> x.getSimpleName());
  }

  public static <F, T> Collection<T> transform(Collection<F> fromCollection, Function<? super F, T> function) {
    return null;
  }
}