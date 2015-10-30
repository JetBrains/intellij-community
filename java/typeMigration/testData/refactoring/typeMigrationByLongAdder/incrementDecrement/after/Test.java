import java.util.concurrent.atomic.AtomicLong;

class Test {
  java.util.concurrent.atomic.LongAdder l = new java.util.concurrent.atomic.LongAdder();

  void foo() {
    l.increment();
    l.increment();
    if (true) {
      l.decrement();
      l.decrement();
    }
  }
}