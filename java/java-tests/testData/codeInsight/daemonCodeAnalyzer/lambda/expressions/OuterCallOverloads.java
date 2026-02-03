import java.util.List;
import java.util.Set;

abstract class Overloadsss {
  abstract <T> List<T> foo(List<T> l);
  abstract <T> Set<T>  foo(Set<T> s);

  abstract <K> List<K> bar1(List<K> l);
  abstract <K> Set<K>  bar1(Set<K> s);

  {
    List<String> l1 = foo(<caret>bar1(null));
  }
}
