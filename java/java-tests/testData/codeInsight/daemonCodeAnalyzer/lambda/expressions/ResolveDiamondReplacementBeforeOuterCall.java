import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

class Test {
  Test(final Set<? super String> set) {
    System.out.println(join(TreeSet.cre<caret>ate(set)));
  }

  public static String join(Iterable<?> items) {return "";}
  public static String join(Collection<String> strings) {return "";}
}

class TreeSet<E> extends Set<E> {
  static <T> TreeSet<T> create(Collection<? extends T> c) { return null;}
}