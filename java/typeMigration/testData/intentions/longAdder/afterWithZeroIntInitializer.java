// "Convert variable to 'java.util.concurrent.atomic.LongAdder'" "true"

public class Main10 {
  void m() {
    java.util.concurrent.atomic.LongAdder i = new java.util.concurrent.atomic.LongAdder();

    i.increment();

    double asDouble = i.doubleValue();

    System.out.println(i.sum());
  }
}