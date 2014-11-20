import java.util.Comparator;

class MyTest {
  void foo(final Ordering<Comparable> natural){
    compound(natural);
  }
  <U extends String> Ordering<U> compound(Comparator<? super U> secondaryComparator) { return null; }
}
abstract class Ordering <T> implements Comparator<T> {}

