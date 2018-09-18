import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

class Atomics {
  private volatile int <warning descr="Private field 'num' is never assigned">num</warning>;
  private static final AtomicIntegerFieldUpdater<Atomics> updater =
    AtomicIntegerFieldUpdater.newUpdater(Atomics.class, "num");

  public int getInt() {
    return updater.get(this);
  }
}