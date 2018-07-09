import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

class Atomics {
  private volatile String str;

  public void set(String s) {
    AtomicReferenceFieldUpdater<Atomics, String> updater = AtomicReferenceFieldUpdater.newUpdater(Atomics.class, String.class, "str");
    updater.set(this, s);
  }
}