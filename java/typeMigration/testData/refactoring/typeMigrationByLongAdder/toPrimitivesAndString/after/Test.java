import java.util.concurrent.atomic.AtomicInteger;

class Test {
  java.util.concurrent.atomic.LongAdder i = new java.util.concurrent.atomic.LongAdder();

  void foo() {
    double v = i.doubleValue();
    float v1 = i.floatValue();
    long l = i.longValue();
    int v3 = this.i.intValue();
    System.out.println(i);
    System.out.println(i.toString());

    i.add(123 - i.sum());
  }
}