import java.util.concurrent.atomic.AtomicLongFieldUpdater;

class Atomics {
  private volatile long num;
  private static final AtomicLongFieldUpdater<Atomics> updater;

  static {
    (updater) = AtomicLongFieldUpdater.newUpdater(Atomics.class, "num");
  }

  public long increment() {
    return updater.incrementAndGet(this);
  }
}