import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

class Atomics {
  private volatile int num;

  public void set(int n) {
    AtomicIntegerFieldUpdater<Atomics> updater = AtomicIntegerFieldUpdater.newUpdater(Atomics.class, "num");
    updater.set(this, n);
  }
}