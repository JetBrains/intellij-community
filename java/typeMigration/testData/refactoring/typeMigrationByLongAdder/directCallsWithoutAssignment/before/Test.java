import java.util.concurrent.atomic.AtomicLong;

class Test {
  AtomicLong l = new AtomicLong(0);

  void foo() {
    l.addAndGet(2);

    l.addAndGet(-3);

    if (l.get() == 5) {
      l.set(7);
    }

    l.set(0);

    System.out.println(l.get() + 9);
    System.out.println(l.floatValue() - 9);
  }
}