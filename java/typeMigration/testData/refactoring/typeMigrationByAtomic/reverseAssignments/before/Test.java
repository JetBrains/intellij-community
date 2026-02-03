import java.util.concurrent.atomic.AtomicInteger;
class Test {
  AtomicInteger i = new AtomicInteger(0);

  void foo() {
    i.getAndAdd(2);
    i.getAndAdd(-5);
    if (i.get() == 0) {
      i.set(9);
    }

    System.out.println(i.addAndGet(9));
    System.out.println(i.addAndGet(-(9)));
  }
}