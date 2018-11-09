import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

class Atomics {
  private volatile String str;
  private static final AtomicReferenceFieldUpdater<Atomics, String> updater =
    AtomicReferenceFieldUpdater.newUpdater(Atomics.class, String.class, "str");

  public void init(String s) {
    updater.compareAndSet(this, null, s);
  }
}