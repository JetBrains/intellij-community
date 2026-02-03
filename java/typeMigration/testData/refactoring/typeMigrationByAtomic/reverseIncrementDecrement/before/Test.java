import java.util.concurrent.atomic.AtomicInteger;
class Test {
  AtomicInteger i;

  void foo() {
    i.getAndIncrement();
    i.incrementAndGet();
    i.getAndDecrement();
    i.decrementAndGet();
    System.out.println(i.getAndIncrement());
    System.out.println(i.decrementAndGet());
  }
}