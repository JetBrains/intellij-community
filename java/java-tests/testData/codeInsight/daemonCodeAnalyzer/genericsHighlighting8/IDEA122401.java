import java.util.Comparator;

class NullComparator<T> {
  private final Comparator<T> real = null;
  private Comparator<? super T> other;
  private Comparator<T> another;

  NullComparator(Comparator<? super T> real) {
  }

  public NullComparator<T> thenComparing() {
    return new NullComparator<>(real == null ? other : another);
  }

  Comparator<T> a() {
    return null;
  }

  Comparator<? super T> b() {
    return null;
  }

  public NullComparator<T> thenComparing1() {
    return new NullComparator<>(real == null ? a() : b());
  }
}
