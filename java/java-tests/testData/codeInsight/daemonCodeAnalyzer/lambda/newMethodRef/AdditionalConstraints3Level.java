import java.util.List;
import java.util.function.Function;

abstract class Sample {
  abstract <T> T       id (T t);
  abstract <R> void    foo(List<R> c);
  abstract <U> List<U> bar(Function<String, U> m);

  {
    foo(bar(this::id));
    foo(bar(id(i -> i)));

    Function<String, String> s = id(this::id);
  }
}