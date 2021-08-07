import java.util.function.Function;


class MyTest {
  void m(Try<String> t)  {
    t.andThen(v -> fa<caret>il());
  }

  private static <V> V fail() {
    return null;
  }
}

interface Try<K> {
  <U> void andThen(Function<K, Try<U>> function);
}
