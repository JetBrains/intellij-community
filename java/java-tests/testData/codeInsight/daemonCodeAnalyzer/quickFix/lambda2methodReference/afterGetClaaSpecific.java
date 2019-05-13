// "Replace lambda with method reference" "true"
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;

class Test {

  public <T> void some(Stream<AtomicReference<T>> stream) {
    stream.map((Function<AtomicReference<T>, ? extends Class<? extends AtomicReference>>) AtomicReference<T>::getClass);
  }


}
