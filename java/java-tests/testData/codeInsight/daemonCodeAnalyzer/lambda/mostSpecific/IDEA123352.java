import java.util.*;

class Test {
  class Predicate<T> {
    private <<warning descr="Type parameter 'S' is never used">S</warning> extends T> boolean test(final Collection<T> src) {
      System.out.println(src);
      return true;
    }
    private <<warning descr="Type parameter 'S' is never used">S</warning> extends T> boolean <warning descr="Private method 'test(java.lang.Iterable<T>)' is never used">test</warning>(final Iterable<T> iterable) {
      System.out.println(iterable);
      return false;
    }
  }

  public void testPredicate() {
    final Predicate<Integer> predicate = new Predicate<>();
    predicate.test(new ArrayList<Integer>());
  }
}