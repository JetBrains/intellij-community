
import java.util.List;
import java.util.function.Function;

import static java.util.Arrays.asList;

class Test {
  public static void bar(List<? extends Number> parameters, Function<Object, String> function)
  {
    Iterable<String> objects = transform(checkNotNull(parameters), function);
  }

  public static <T> T checkNotNull(T reference) {
    return reference;
  }

  public static <F, T> List<T> transform(final List<F> fromIterable, final Function<? super F, ? extends T> function) {
    return null;
  }

  {
    List<List<List<Integer>>> list = asList(asList(asList(1)), asList(asList(2)));
  }
}