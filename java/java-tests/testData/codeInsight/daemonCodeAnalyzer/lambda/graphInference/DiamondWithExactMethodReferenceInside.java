import java.util.*;
import java.util.function.UnaryOperator;

class Test {

  public static Optional<String> bar(Optional<String> o) {
    return null;
  }

  public <T> D<T> foo(D<T> entries) {
    return null;
  }

  {
    D<UnaryOperator<Optional<String>>> registry = foo(new D<>(Test::bar));
  }

  static class D<V> {
    public D(V value) {}
  }
}