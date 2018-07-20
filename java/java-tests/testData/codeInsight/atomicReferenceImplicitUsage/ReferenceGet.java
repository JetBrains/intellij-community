import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

class Atomics {
  private volatile String <warning descr="Private field 'str' is never assigned">str</warning>;
  private static final AtomicReferenceFieldUpdater<Atomics, String> updater =
    AtomicReferenceFieldUpdater.newUpdater(Atomics.class, String.class, "str");

  public String getStr() {
    return updater.get(this);
  }
}