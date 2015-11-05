import java.util.concurrent.atomic.AtomicLong;

class Test {
  AtomicLong l = new AtomicLong(0L);

  void foo() {
    l.incrementAndGet();
    l.getAndIncrement();
    if (true) {
      l.decrementAndGet();
      l.decrementAndGet();
    }
  }
}