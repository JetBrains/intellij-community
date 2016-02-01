
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

class Test {
  public <T> void some() {
    Function<AtomicReference<T>, ? extends Class<? extends AtomicReference>> a = AtomicReference<T>::getClass;
    Function<AtomicReference<T>, ? extends Class<? extends AtomicReference>> b = AtomicReference::getClass;
  }
}
