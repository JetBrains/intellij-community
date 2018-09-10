import java.util.concurrent.atomic.AtomicLongFieldUpdater;

class Atomics {
  private volatile long <warning descr="Private field 'num' is never assigned">num</warning>;
  private static final AtomicLongFieldUpdater<Atomics> updater =
    AtomicLongFieldUpdater.newUpdater(Atomics.class, "num");

  public long getLong() {
    return updater.get(this);
  }
}