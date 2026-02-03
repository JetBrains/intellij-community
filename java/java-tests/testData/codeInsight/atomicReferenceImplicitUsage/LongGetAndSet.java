import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.function.BiFunction;

class Atomics {
  private volatile long num;
  private static final AtomicLongFieldUpdater<Atomics> updater =
    AtomicLongFieldUpdater.newUpdater(Atomics.class, "num");

  public long getLong() {
    return updater.get(this);
  }

  public long getAndSet(long n) {
    return update(updater::getAndSet, n);
  }

  private long update(BiFunction<Atomics, Long, Long> f, long n) {
    return f.apply(this, n);
  }
}