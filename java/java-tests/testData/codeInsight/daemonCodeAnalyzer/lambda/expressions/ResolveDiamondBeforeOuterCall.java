import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

class Test {
  Test(final Set<? super String> set) {
    System.out.println(join(new TreeSet<>(set), ""));
  }

  public static String join(Iterable<?> items, String separator) {return items+separator;}
  public static String join(Collection<String> strings, String separator) {return strings + separator;}
}