import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.BiFunction;

class Atomics {
  private volatile int num;
  private static final AtomicIntegerFieldUpdater<Atomics> updater =
    AtomicIntegerFieldUpdater.newUpdater(Atomics.class, "num");

  public int getInt() {
    return updater.get(this);
  }

  public int getAndSet(int n) {
    return update(updater::getAndSet, n);
  }

  private int update(BiFunction<Atomics, Integer, Integer> f, int n) {
    return f.apply(this, n);
  }
}