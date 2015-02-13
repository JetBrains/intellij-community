import java.util.function.Supplier;

class Test {
  {
    Supplier<Runnable> x = foo(() -> () -> {});
  }

  static <T> Supplier<T> foo(Supplier<T> delegate) {
    return null;
  }
}
