import java.util.concurrent.atomic.AtomicLong;

class Test {
  java.util.concurrent.atomic.LongAdder l = new java.util.concurrent.atomic.LongAdder();

  void foo() {
    l.add(2);

    l.add(-3);

    if (l.sum() == 5) {
      l.add(7 - l.sum());
    }

    l.reset();

    System.out.println(l.sum() + 9);
    System.out.println(l.floatValue() - 9);
  }
}