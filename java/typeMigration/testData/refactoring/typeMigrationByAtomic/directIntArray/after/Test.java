import java.util.concurrent.atomic.AtomicIntegerArray;

class Test {
  AtomicIntegerArray a = new AtomicIntegerArray(new int[1]);


  void foo() {
      a.getAndIncrement(0);
      System.out.println(a.incrementAndGet(0));
      a.getAndDecrement(0);
      if (a.decrementAndGet(0) == 0) {
          a.getAndAdd(0, (2));
          a.set(0, a.get(0) * 2);
          if (a.get(0) == 0) {
              System.out.println(a.get(0) + 7);
          }
      }
      a = new AtomicIntegerArray(new int[0]);
  }
}