// "Convert variable to 'java.util.concurrent.atomic.LongAdder'" "true"
import java.util.concurrent.atomic.AtomicInteger;

public class Main10 {
  void m() {
    AtomicInteger <caret>i = new AtomicInteger(0);

    i.incrementAndGet();

    double asDouble = i.doubleValue();

    System.out.println(i.get());
  }
}