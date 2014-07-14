import java.util.*;

class Test {
  class Predicate<T> {
    <S extends T> boolean test(final Collection<T> src) {
      return true;
    }
    <S extends T> boolean test(final Iterable<T> iterable) {
      return false;
    }
  }

  public void testPredicate() {
    final Predicate<Integer> predicate = new Predicate<>();
    predicate.test(new ArrayList<Integer>());
  }
}