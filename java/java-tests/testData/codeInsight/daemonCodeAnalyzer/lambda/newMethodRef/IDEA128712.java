import java.util.Collection;
import java.util.function.Function;
import java.util.stream.Stream;

abstract class Foo {
  {
    map(Foo::bar);
    map(a -> Foo.bar(a));
  }

  <R> Stream<R> map(Function<Class<?>, ? extends R> mapper) {
    return null;
  }


  private static <T> Collection<T> bar(Class<T> baseClass) {
    return null;
  }
}
