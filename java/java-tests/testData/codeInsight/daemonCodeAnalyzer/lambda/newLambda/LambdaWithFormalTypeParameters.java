
import java.io.IOException;
import java.util.Optional;

class GenericClass {
  <E extends Throwable> void build(Test<E, Optional<Integer>> test) { }

  {
    build((IOException ex) ->  returnOptional());
  }

  private static <T> Optional<T> returnOptional() {
    return null;
  }

  @FunctionalInterface
  interface Test<E, T> {
    T evaluate(E ex);
  }
}