import java.util.concurrent.atomic.AtomicLongFieldUpdater;

class Atomics {
  private volatile long num;

  public void set(long n) {
    AtomicLongFieldUpdater<Atomics> updater = AtomicLongFieldUpdater.newUpdater(Atomics.class, "num");
    updater.set(this, n);
  }
}