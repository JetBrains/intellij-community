import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

class Atomics {
  private volatile int num;
  private static final AtomicIntegerFieldUpdater<Atomics> updater =
    AtomicIntegerFieldUpdater.newUpdater(Atomics.class, "num");

  public void init(int n) {
    (updater).compareAndSet(this, 0, n);
  }
}