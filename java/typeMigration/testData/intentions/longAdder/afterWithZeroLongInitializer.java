// "Convert variable to 'java.util.concurrent.atomic.LongAdder'" "true"

public class Main10 {
  void m() {
    java.util.concurrent.atomic.LongAdder l = new java.util.concurrent.atomic.LongAdder();

    l.decrement();

    System.out.println(l.floatValue());

    l.add(12 - l.sum());
  }
}