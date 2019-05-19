import java.util.concurrent.atomic.AtomicLongFieldUpdater;

class Atomics {
  private volatile long num;
  private static final AtomicLongFieldUpdater<Atomics> updater =
    (AtomicLongFieldUpdater.newUpdater(Atomics.class, "num"));

  public void init(long n) {
    updater.compareAndSet(this, 0, n);
  }
}