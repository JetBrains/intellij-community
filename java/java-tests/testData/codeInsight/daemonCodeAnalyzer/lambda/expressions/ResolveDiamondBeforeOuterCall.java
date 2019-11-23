import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

class Test {
  Test(final Set<? super String> set) {
    System.out.println(join(new TreeSet<>(set)));
  }

  public static String join(Iterable<?> items) {return "";}
  public static String join(Collection<String> strings) {return "";}
}

class TreeSet<E> extends Set<E> {
  TreeSet(Collection<? extends E> c) {}
}