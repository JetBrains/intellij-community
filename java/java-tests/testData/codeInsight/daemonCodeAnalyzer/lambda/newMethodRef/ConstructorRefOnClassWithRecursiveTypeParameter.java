
import java.util.List;
import java.util.function.Function;


class Foo<T extends Comparable<T>> {

  public Foo(final Foo<T> tuple) { }

  {
    List<Foo<T>> l = bar(Foo::new);
  }

  public<U> List<U> bar(Function<Foo<T>, U> mapper) {
    return null;
  }

}