import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.BiFunction;

class Atomics {
  private volatile String str;
  private static final AtomicReferenceFieldUpdater<Atomics, String> updater =
    AtomicReferenceFieldUpdater.newUpdater(Atomics.class, String.class, "str");

  public String getAndSet(String s) {
    return update(updater::getAndSet, s);
  }

  private String update(BiFunction<Atomics, String, String> f, String s) {
    return f.apply(this, s);
  }
}