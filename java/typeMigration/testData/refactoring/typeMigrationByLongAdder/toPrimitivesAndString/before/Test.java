import java.util.concurrent.atomic.AtomicInteger;

class Test {
  AtomicInteger i = new AtomicInteger(0);

  void foo() {
    double v = i.doubleValue();
    float v1 = i.floatValue();
    long l = i.longValue();
    int v3 = this.i.intValue();
    System.out.println(i);
    System.out.println(i.toString());

    i.getAndSet(123);
  }
}