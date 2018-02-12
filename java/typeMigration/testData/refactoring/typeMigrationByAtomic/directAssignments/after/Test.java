import java.util.concurrent.atomic.AtomicInteger;

class Test {
  AtomicInteger i = new AtomicInteger();

  void foo() {
    i.addAndGet(2);
    i.addAndGet(-5);
    if (i.get() == 0) {
      i.set(9);
    }

    System.out.println(i.get() + 9);
    System.out.println(i.get() - 9);
  }
}