
import java.util.Collection;
import java.util.LinkedList;
import java.util.Optional;
import java.util.function.BiFunction;

class Main {

  public static void main(String[] args) {
    Collection<Integer> result = new LinkedList<>();
    Optional<A> _1 = Optional.of(new A());
    Optional<B> _2 = Optional.of(new B());
    BiFunction<Integer, Integer, Boolean> le = (a, b) -> a <= b;

    addIfTrue(result, compareIfPresent(_1.map(A::getValue), _2.map(B::getValue), le), 3);

    System.out.println(result);
  }

  private static <T, U> boolean compareIfPresent(Optional<T> arg1, Optional<U> arg2, BiFunction<T, U, Boolean> comparator) {
    if (!arg1.isPresent()) return true;
    if (!arg2.isPresent()) return true;
    return comparator.apply(arg1.get(), arg2.get());
  }

  public static <T> void addIfTrue(Collection<T> list, boolean canAdd, T t) {
    if (canAdd) {
      addIfNotNull(list, t);
    }
  }

  public static <T> void addIfNotNull(Collection<T> list, T t) {
    if (t != null) {
      list.add(t);
    }
  }

  static class A {
    public Integer getValue() {
      return 1;
    }
  }

  static class B {
    public Integer getValue() {
      return 2;
    }
  }
}