import java.util.List;
import java.util.Set;

abstract class Overloadsss {
  abstract <T> List<T> foo(List<T> l);
  abstract <T> Set<T>  foo(Set<T> s);

  abstract <K> List<K> bar(List<K> l);
  abstract <K> List<K> bar1(List<K> l);
  abstract <K> Set<K>  bar1(Set<K> s);

  {
    List<String> l  = foo(bar (null));
    List<String> l1 = foo(bar1<error descr="Ambiguous method call: both 'Overloadsss.bar1(List<K>)' and 'Overloadsss.bar1(Set<K>)' match">(null)</error>);
  }
}
