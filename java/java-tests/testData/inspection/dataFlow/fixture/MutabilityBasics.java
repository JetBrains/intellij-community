import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class MutabilityBasics {
  @Unmodifiable
  static <T> List<T> emptyList() {
    return Collections.emptyList();
  }

  @Contract(<error descr="Cannot resolve method 'mutates'">mutates</error> = "arg")
  static <T extends Comparable<T>> void sort(List<T> collection) {
    Collections.sort(collection);
  }

  @Contract(<error descr="Cannot resolve method 'mutates'">mutates</error> = "arg1")
  static <T extends Comparable<T>> void addAll(Collection<T> collection, List<T> other) {
    sort(<warning descr="Immutable object is passed where mutable is expected">other</warning>);
    collection.addAll(other);
  }

  // Purity implies that no arguments should be changed
  @Contract(pure = true)
  static <T extends Comparable<T>> T min(List<T> list) {
    sort(<warning descr="Immutable object is passed where mutable is expected">list</warning>);
    return list.get(0);
  }

  interface Point {
    int get();

    @Contract(<error descr="Cannot resolve method 'mutates'">mutates</error> = "this")
    void set(int x);

    @Contract(pure = true)
    default void setZero() {
      // cannot modify itself (call mutating method), because declared as pure
      <warning descr="Immutable object is modified">set</warning>(0);
    }
  }

  @Unmodifiable
  static Point getZero() {
    return new Point() {
      @Override
      public int get() {
        return 0;
      }

      @Override
      public void set(int x) {
        throw new UnsupportedOperationException();
      }
    };
  }

  // Differs from getZero as getZero() is considered as getter with predefined value
  @Unmodifiable
  static Point zero() {
    return getZero();
  }

  @Unmodifiable List<String> list = Arrays.asList("foo", "bar", "baz");

  void test() {
    List<String> collection = emptyList();
    sort(<warning descr="Immutable object is passed where mutable is expected">collection</warning>);
    sort(<warning descr="Immutable object is passed where mutable is expected">MutabilityBasics.<String>emptyList()</warning>);
    getZero().<warning descr="Immutable object is modified">set</warning>(1);
    zero().<warning descr="Immutable object is modified">set</warning>(1);
    sort(<warning descr="Immutable object is passed where mutable is expected">list</warning>);
  }
}
