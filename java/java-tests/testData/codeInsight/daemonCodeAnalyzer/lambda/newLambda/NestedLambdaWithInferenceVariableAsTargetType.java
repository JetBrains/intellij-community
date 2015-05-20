import java.util.function.Supplier;

class Test {
  {
    Supplier<Runnable> x = foo(() -> () -> {});
  }

  static <T> Supplier<T> foo(Supplier<T> delegate) {
    return null;
  }
}

class Test1 {
  {
    Supplier<Runnable> x = foo(() -> <error descr="Multiple non-overriding abstract methods found in interface java.util.List">() -> null</error>);
  }

  static <T> Supplier<T> foo(Supplier<java.util.List<T>> delegate) {
    return null;
  }
}