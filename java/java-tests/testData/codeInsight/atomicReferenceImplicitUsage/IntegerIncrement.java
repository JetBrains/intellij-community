import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

class Atomics {
  private volatile int num;
  private static final AtomicIntegerFieldUpdater<Atomics> updater;

  static {
    updater = (AtomicIntegerFieldUpdater.newUpdater(Atomics.class, "num"));
  }

  public int increment() {
    return updater.incrementAndGet(this);
  }
}