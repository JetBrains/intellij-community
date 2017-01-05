import java.util.concurrent.atomic.AtomicLong;

// "Convert to atomic" "true"
class A {
  final AtomicLong x = new AtomicLong(0);

  public void testAtomicLong() {
    x.getAndIncrement();
    x.getAndDecrement();
    x.addAndGet(2);
    x.addAndGet(-2);
    x.updateAndGet(v -> v * 3);
    x.updateAndGet(v -> v / 3);
    x.updateAndGet(v -> v % 3);
    x.updateAndGet(v -> v & 3);
    x.updateAndGet(v -> v | 3);
  }
}