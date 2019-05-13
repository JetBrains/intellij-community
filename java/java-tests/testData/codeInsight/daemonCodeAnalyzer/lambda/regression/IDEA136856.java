import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collector;

class Test {
  public static final <T> Collector<T, AtomicReference<T>, Optional<T>> toSingleton() {
    return Collector.of(
      AtomicReference::new,
      (ref, v) -> {
        if (!ref.compareAndSet(null, v) && v != null) {
          throw new IllegalStateException("There is only one elvis.");
        }
      },
      (left, right) -> setOrFail(left, right.get()),
      ref -> Optional.of(ref.get())
    );
  }

  public static final <T> Collector<T, AtomicReference<T>, Optional<T>> toSingleton1() {
    return Collector.of(
      AtomicReference::new,
      (ref, v) -> setOrFail(ref, v),
      (left, right) -> setOrFail(left, right.get()),
      ref -> Optional.of(ref.get())
    );
  }

  public static final <T> Collector<T, AtomicReference<T>, Optional<T>> toSingleton2() {
    return Collector.of(
      AtomicReference::new,
      Test::setOrFail,
      (left, right) -> setOrFail(left, right.get()),
      ref -> Optional.of(ref.get())
    );
  }    

  private static final <T> AtomicReference<T> setOrFail(AtomicReference<T> ref, T v) {
    if (!ref.compareAndSet(null, v) && v != null) {
      throw new IllegalStateException("There is only one elvis.");
    }
    return ref;
  }
}