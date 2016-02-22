import java.util.List;
import java.util.Set;

abstract class Test {

  public void foo(List list) {
    set<error descr="Ambiguous method call: both 'Test.set(Set<List>, List)' and 'Test.set(Set, List)' match">(get(), list)</error>;
  }

  abstract <Y> Set<Y> get();

  abstract <Y, X extends Y> void set(Set<Y> set, X x);
  abstract <Y>              void set(Set<Y> set, List<? extends Y> l);
}
